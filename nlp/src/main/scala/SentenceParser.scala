package com.graphbrain.nlp

import java.net.URLDecoder;
import scala.collection.immutable.HashMap
import scala.util.Sorting
import com.graphbrain.hgdb.VertexStore
import com.graphbrain.hgdb.OpLogging
import com.graphbrain.hgdb.TextNode
import com.graphbrain.hgdb.Edge
import com.graphbrain.hgdb.URLNode
import com.graphbrain.hgdb.UserNode
import com.graphbrain.hgdb.EdgeType
import com.graphbrain.hgdb.Vertex
import com.graphbrain.hgdb.ID
import com.graphbrain.hgdb.SearchInterface

class SentenceParser (storeName:String = "gb") {

  val store = new VertexStore(storeName)

  val quoteRegex = """(\")(.+?)(\")""".r
  val nodeRegex = """(\[)(.+?)(\])""".r
  val hashRegex = """#""".r
  val disambigRegex = """(\()(.+?)(\))""".r
  val urlRegex = """([\d\w]+?:\/\/)?([\w\d\.\-]+)(\.\w+)(:\d{1,5})?(\/\S*)?""".r // See: http://stackoverflow.com/questions/8725312/javascript-regex-for-url-when-the-url-may-or-may-not-contain-http-and-www-words?lq=1
  val urlStrictRegex = """(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&amp;:/~\+#]*[\w\-\@?^=%&amp;/~\+#])?""".r
  val gbNode = store.createTextNode(namespace="1", text="GraphBrain")
  val asInRel = ID.reltype_id("as in", 1)

  val verbRegex = """VB[A-Z]?""".r
  val adverbRegex = """RB[A-Z]?""".r
  val prepositionRegex = """(IN[A-Z]?)|TO""".r
  val relRegex = """(VB[A-Z]?)|(RB[A-Z]?)|(IN[A-Z]?)|TO|DT""".r
  val nounRegex = """NN[A-Z]?""".r
  val leftParenthPosTag = """-LRB-""".r
  val rightParenthPosTag = """-RRB-""".r

  val imageExt = List("""[^\s^\']+(\.(?i)(jpg))""".r, """[^\s^\']+(\.(?i)(jpeg))""".r, """[^\s^\']+(\.(?i)(gif))""".r, """[^\s^\']+(\.(?i)(tif))""".r, """[^\s^\']+(\.(?i)(png))""".r, """[^\s^\']+(\.(?i)(bmp))""".r, """[^\s^\']+(\.(?i)(svg))""".r)
  val videoExt = List("""http://www.youtube.com/watch?.+""".r, """http://www.vimeo.com/.+""".r, """http://www.dailymotion.com/video.+""".r)
  val gbNodeExt = """(http://)?graphbrain.com/node/""".r
  val rTypeMapping = HashMap("is the opposite of "->"is the opposite of", "means the same as " -> "means_the_same_as", "is a "->"is_a", "is an "->"is_an",  "is a type of "->"is_a_type_of", "has a "->"has_a")

  //"is" needs to be combined with POS to determine whether it is a property, state, or object that is being referred to.
  val questionWords = List("do", "can", "has", "did", "where", "when", "who", "why", "will", "how")

  val searchWords = List("search", "find", "search for", "look for", "look up")

  //val posTagger = new POSTagger()
  val lemmatiser = new Lemmatiser()

  val si = new SearchInterface(store)

  //Returns a float value indicating level of certainty (relative to other values)
  def isQuestion(text:String): Double = {
    //Quite stringent - checks to see whether entire text is a question i.e. starts with a question mark, ends with a question mark.
    for(qWord <- questionWords) {
      if(text.toLowerCase.startsWith(qWord)) {

        if(text.endsWith("?")) {
          1
        }
      }
    }
    0
  }

  def isSearch(text: String): (Double, String) = {
    for(searchWord <- searchWords) {
      if(text.toLowerCase.startsWith(searchWord)) {

        return (1, text.substring(searchWord.length, text.length).replace("`", "").replace("\"", "").replace("'", "").trim)
      }
    }
    val posTags = lemmatiser.posTag(text);
    for(tag <- posTags) {
      if(verbRegex.findAllIn(tag._2).hasNext) {
        (0, "")
      }
    }
    (0.5, text)
    
  }
  def specialNodeCases(inNodeText: String, root: Vertex = store.createTextNode(namespace="", text="GBNoneGB"), user: Option[UserNode]=None): Vertex = {
    user match {
      case Some(u:UserNode) =>
        if(u.username == inNodeText || u.name == inNodeText || inNodeText == "I" || inNodeText == "me") {
          return u;
        }
      case _ => 
    }

    root match {
      case a: TextNode =>
        if(a.text == inNodeText || a.text.toLowerCase.indexOf(inNodeText.toLowerCase)==0 || inNodeText.toLowerCase.indexOf(a.text.toLowerCase) == 0) {
          return a;
        }
        //Check whether already in database - global and user; create new node if necessary
        user match {
          case Some(u:UserNode) => 
            val userThingID = ID.usergenerated_id(u.username, a.text)
            
            if(nodeExists(userThingID)) {
              if(inNodeText==a.text) {
                return a;
              
              }
            }
          case _ => 
        }
      case _ =>
    }
    return textToNode(inNodeText, user=user)(0);

  }

  def reparseGraphTexts(nodeTexts: List[String], relText: String, disambigs: List[(String, String)], root: Vertex = store.createTextNode(namespace="", text="GBNoneGB"), user: Option[UserNode]=None): (List[(Vertex, Option[Vertex])], Vertex) = {
    var solutions: List[(List[(Vertex, Option[Vertex])], Vertex)] = List()
    var tempDisambs = disambigs;
    var nodes: List[(Vertex, Option[Vertex])] = List()
      
    val sepRelations = """~""".r.split(relText)
    var i = 0;
      
    var newRelation = ""
          
    for(nodeText <- nodeTexts) {
      var d = "";
      var dNode: Option[Vertex] = None;

      if(tempDisambs.length > 0){
        if(nodeText == tempDisambs.head._1) {
          d = tempDisambs.head._2;
          dNode = Some(specialNodeCases(d, root, user))

          tempDisambs = tempDisambs.tail;
        }
      }
        
      if(nodeText.toLowerCase == "you" || nodeText.toUpperCase == "I") {
        if(nodeText.toLowerCase == "you") {
          nodes = (gbNode, dNode) :: nodes;
        } 
        else {
          user match {
            case Some(u: UserNode) => nodes = (u, dNode) :: nodes;
            case _ => 
          }
        }
          
        if(i < sepRelations.length) {
            
          val annotatedRelation = lemmatiser.annotate(sepRelations(i))    
          for (a <- annotatedRelation) {
            if(verbRegex.findAllIn(a._2).hasNext) {
              newRelation += lemmatiser.conjugate(a._3) + " "
            }
            else {
              newRelation += a._1 + " "
            }

          }
            
        }

      }
      else {
        nodes = (specialNodeCases(nodeText, root, user), dNode) :: nodes;
        if(i < sepRelations.length) {
          newRelation += sepRelations(i) + " ";  
        }
          
      }
      newRelation = newRelation.trim;
      if(i < sepRelations.length-1) {
        newRelation += "~"
      }
        
      i += 1;
    }
    val newRelText = newRelation.trim.slice(0, newRelation.length).trim;
    var relationV = store.createEdgeType(id = ID.reltype_id(newRelText), label = newRelText)
    return (nodes.reverse, relationV)

  }


  def parseSentenceGeneral(inSent: String, root: Vertex = store.createTextNode(namespace="", text="GBNoneGB"), user: Option[UserNode]=None): List[ResponseType] = {
    var inSentence = inSent;
    
    var responses : List[ResponseType] = List();

    val search = isSearch(inSentence)
    val question = isQuestion(inSentence)

    if(question > search._1 && question > 0.5) {
      responses = HardcodedResponse(List("Sorry, I don't understand questions yet.")) :: responses
    }
    else if (search._1 > 0.5){
      responses = SearchResponse(List(search._2)) :: responses
    }
 
    //Only remove full stop or comma at the end of sentence (allow other punctuation since likely to be part of proper name e.g. film titles)
    if(inSentence.endsWith(".")) {
      inSentence = inSentence.slice(0, inSentence.length-1)
    }

    //Try segmenting with square bracket syntax.
    var parses = strictChunk(inSentence, root);

    //Check for disambiguation syntax

    
    //Only parse with POS if nothing returned:
    if(parses==(Nil, "", Nil)) {
      parses = posChunkGeneral(inSentence, root)
    }
    val solutions = reparseGraphTexts(parses._1, parses._2, parses._3, root, user);

    responses = GraphResponse(solutions::Nil) :: responses

    if(question > search._1 && question <= 0.5) {
      responses = HardcodedResponse(List("Sorry, I don't understand questions yet.")) :: responses
    }
    else if (search._1 <= 0.5){
      responses = SearchResponse(List(search._2)) :: responses
    }

    //This will be the first in the list - so parsing favours graph responses over hardcoded etc.
    
    return responses.reverse;
    
    
  }

  def textToNode(text:String, node: Vertex = store.createTextNode(namespace="", text="GBNoneGB"), user:Option[Vertex]=None): List[Vertex] = {
    var userName = "";
    var results: List[Vertex] = List()
    user match {
        case Some(u:UserNode) => 
          userName = u.username;
          val name = u.name
          if(text.toLowerCase.indexOf(userName.toLowerCase) == 0 || userName.toLowerCase.indexOf(text.toLowerCase) == 0 ||text.toLowerCase.indexOf(name.toLowerCase) == 0 || name.toLowerCase.indexOf(text.toLowerCase) == 0 ) {
            results = u :: results;
          }
        case _ => 

    }
   
    if(nodeExists(text)) {
      try{
        results = store.get(text) :: results;        
      }
      catch {case e =>}

    }

    if(gbNodeExt.split(text).length==2) {
      val gbID = gbNodeExt.split(text)(1)
      
      if(nodeExists(gbID)) {
        
        try {
          results = store.get(gbID) :: results;
        }
      }
    }

    if (urlRegex.findAllIn(text).hasNext) {
      
      val urlNode = URLNode(store, text)
      results = store.createURLNode(url = text, userId = "") :: results;
      
    }
    val textPureID = ID.text_id(text, 1)
    val wikiID = ID.wikipedia_id(text)

    
    if(nodeExists(textPureID)) {
      results = getOrCreate(textPureID) :: results;  
    }
    
    var i = 1;
    while(nodeExists(ID.text_id(text, i)))
    {
      results = store.createTextNode(namespace=i.toString, text=text) :: results;
      i += 1;
        
    }
    if(i==1) {
      results = store.createTextNode(namespace="1", text = text) :: results;
    }
    return results.reverse
    
  }

 
  /**
  Returns lemma node and pos relationship type (linking the two edge types).
  */
  def relTypeLemmaAndPOS(relType: EdgeType, sentence: String): (EdgeType, (TextNode, EdgeType)) = {
    
    /*if(relType.label == "is a"||relType.label == "is an") {
      val isLemmaNode = TextNode(id = ID.text_id("be", 1), text = "be")
      val isRelType = EdgeType(id = ID.reltype_id("VBZ"), label = "VBZ")
      return (relType, (isLemmaNode, isRelType))
    }*/
    val allRelTypes = """~""".r.split(relType.label)
    val posSentence = lemmatiser.annotate(sentence)
    var lemma = ""
    var poslabel = ""
    for (rType <- allRelTypes) {

      
      val splitRelType = """\s""".r.split(rType)

      for(i <- 0 to splitRelType.length-1) {
        val relTypeComp = splitRelType(i).trim
        
        for (tagged <- posSentence) {
        
          if(tagged._1 == relTypeComp) {
            poslabel += tagged._2 + "_";
            lemma += tagged._3 + "_";
          }
          

        }
      }
      poslabel = poslabel.slice(0, poslabel.length).trim + "~"
      lemma = lemma.slice(0, lemma.length).trim + "~"

    //Remove the last "_"
    }
    poslabel = poslabel.slice(0, poslabel.length-2).trim
    lemma = lemma.slice(0, lemma.length-2).trim
     
    val lemmaNode = store.createTextNode(namespace="1", text=lemma)
    val lemmaRelType = store.createEdgeType(id = ID.reltype_id(poslabel), label = poslabel)
    return (relType, (lemmaNode, lemmaRelType));
    
  }

def strictChunk(sentence: String, root: Vertex): (List[String], String, List[(String, String)]) = {
  
  val nodeTexts = nodeRegex.findAllIn(sentence);
  if(!nodeTexts.hasNext) {
    return (Nil, "", Nil);
  }
  val edgeTexts = nodeRegex.split(sentence);
  var nodes: List[String] = List()
  var edge = ""
  for(nodeText <- nodeTexts) {
    nodes = nodeText.replace("[", "").replace("]", "").trim:: nodes
  }
  //Index from 1 since first element is discarded
  for(i <- 1 to edgeTexts.length-1) {
    edge += edgeTexts(i).trim.replace(" ", "_") + "~";
  }
  edge = edge.slice(0, edge.length-1)
  return (nodes, edge, Nil)

}

  


def posChunkGeneral(sentence: String, root: Vertex): (List[String], String, List[(String, String)])={
  val sanSentence = TextFormatting.deQuoteAndTrim(sentence)
  
  var taggedSentence = lemmatiser.posTag(sanSentence);
  var quoteTaggedSentence = InputSyntax.quoteAndParenthTag(sentence);
  
  var inEdge = false;
  var inQuote = false;
  var quoteCounter = 0;

  var nodeTexts: List[String] = List()
  var disambigs: List[(String, String)] = List() //First tuple stores the text, the second stores the disambiguation.
  var edgeText = ""
  var nodeText = ""
  var currentSplitQuote =""


  while(taggedSentence.length > 1) {
      
    val current = taggedSentence.head
    val lookahead = taggedSentence.tail.head
    val currentQuote = quoteTaggedSentence.head
    val nextQuote = quoteTaggedSentence.tail.head
   

    (current, lookahead, currentQuote, nextQuote) match{
        case ((word1, tag1), (word2, tag2), (qw1, qt1), (qw2, qt2)) => 
        
        
        if(qt1=="InQuote") {

          nodeText += qw1 + " ";
          if(qt2=="NonQuote") {
            nodeTexts = TextFormatting.deQuoteAndTrim(nodeText) :: nodeTexts;
            nodeText = ""
          }
          
        }

        else if((relRegex.findAllIn(tag1).length == 1)) {
          edgeText += word1.trim + " "

          if(relRegex.findAllIn(tag2).length == 0) {
            edgeText = edgeText.trim + "~"
              
          }

          
        }
        else if (relRegex.findAllIn(tag1).length == 0) {
          nodeText += word1.trim + " "

          if(relRegex.findAllIn(tag2).length == 1) {

            nodeTexts = TextFormatting.deQuoteAndTrim(nodeText) :: nodeTexts;
            nodeText = ""
          }
          
        }
        if (taggedSentence.length == 2) {

          nodeText += word2.trim;
          nodeTexts = TextFormatting.deQuoteAndTrim(nodeText) :: nodeTexts;

        }
        if(leftParenthPosTag.findAllIn(tag1).length == 1) {
          val parenthProcessed = InputSyntax.disambig(nodeText.head.toString, disambigs, taggedSentence, quoteTaggedSentence);
          disambigs = parenthProcessed._1;
          taggedSentence = parenthProcessed._2;
          quoteTaggedSentence = parenthProcessed._3;
        }
        
        
      }
     
      taggedSentence = taggedSentence.tail;
      quoteTaggedSentence = quoteTaggedSentence.tail;

    }
    
    nodeTexts = nodeTexts.reverse;
    edgeText = edgeText.substring(0, edgeText.length-1);
    


    return (nodeTexts, edgeText, disambigs);
  }

def findOrConvertToVertices(possibleParses: List[(List[String], String)], root: Vertex, user:Option[Vertex], maxPossiblePerParse: Int = 10): List[(List[Vertex], Edge)]={
    
    var userID = ""
    user match {
        case u:UserNode => userID = u.username;
        case _ => 

    }
	var possibleGraphs:List[(List[Vertex], Edge)] = List()
	val sortedParses = removeDeterminers(sortRootParsesPriority(possibleParses, root), root)

  println("Sorted parses: " + sortedParses.length)

	for (pp <- sortedParses) {
		pp match {
			case (nodeTexts: List[String], edgeText: String) => 
			var nodesForEachNodeText = new Array[List[Vertex]](nodeTexts.length)
      var countsForEachNodeText = new Array[Int](nodeTexts.length)
			
			var edgesForEdgeText: List[Edge] = List()
			var textNum = 0;
			
      for (nodeText <- nodeTexts) {
				val results = si.query(nodeText)
				
				//fuzzy search results are second in priority
				var currentNodesForNodeText:List[Vertex] = List() 
				val limit = if (maxPossiblePerParse < results.length) maxPossiblePerParse else results.length;
        println("Limit: " + limit)
				for(i <- 0 to limit-1) {
				  val result = try {results(i) } catch { case e => ""}
				  val resultNode = getOrCreate(result, user, nodeText, root)
				  println("Node: " + resultNode.id)

				  currentNodesForNodeText = resultNode :: currentNodesForNodeText;
				}
        //Result for a new node to be created
        val resultNode = getOrCreate("", user, nodeText, root)
        currentNodesForNodeText = resultNode :: currentNodesForNodeText;
				nodesForEachNodeText(textNum) = currentNodesForNodeText;
        countsForEachNodeText(textNum) = currentNodesForNodeText.length;
				textNum += 1;

			}
      Sorting.quickSort(countsForEachNodeText)
      val minNodes = (countsForEachNodeText)(0)

      //TODO Fix this properly! At the moment, I just get the minimum 
		  for (i <- 0 to minNodes-1) {

		    var entryNodes:List[Vertex] = List();
			  var entryIDs:List[String] = List();

			  entryNodes = nodesForEachNodeText(0)(i) :: entryNodes;
			  entryNodes = nodesForEachNodeText(1)(i) :: entryNodes;
			  entryIDs = nodesForEachNodeText(0)(i).id :: entryIDs
			  entryIDs = nodesForEachNodeText(1)(i).id :: entryIDs

			  val edge = new Edge(ID.relation_id(edgeText), entryIDs.reverse)
			  println("Edge: " + edge)
			  val entry = (entryNodes, edge)
			  
			  possibleGraphs = entry :: possibleGraphs
			}
			  
			  
		}

	 }
	 return possibleGraphs.reverse
	}
	
  
  
  /**
Sorts the parses so that only the ones consistent with the root node being one of the nodes is returned
If returnAll is false, only return the ones that satisfy the root as node constraint, if true, return all results sorted
*/
  def sortRootParsesPriority(possibleParses: List[(List[String], String)], rootNode: Vertex, returnAll: Boolean = true): List[(List[String], String)]={

  	//possibleParses contains the parses that are consistent with the root being a node from a linguistic point of view
  	var rootParses: List[(List[String], String)] = List()
  	var optionalParses: List[(List[String], String)] = List()
  	rootNode match {
  		case a: TextNode => val rootText = a.text.r;
  		for (g <- possibleParses) {
  			g match {
  				case (nodeTexts: List[String], edgeText: String) => 
            var optComplete = false;
  				  for(nodeText <- nodeTexts) {

  				  	if (nodeText==rootText) {
  				  		rootParses = g::rootParses
                //If the root text appears in more than one node (e.g. self-referencing), allow both possibilities
  				  	}
  				  	else if(optComplete==false) {
  				  		 optionalParses = g::optionalParses
                 optComplete=true;
  				  	}
  				  }
  			}
		  //Check whether rootText matches one of the node texts:
		  	    			
  		}
  	  
  	}
  	if(returnAll) {
      
  	 return rootParses.reverse++optionalParses.reverse
  	}
  	else {
  		return rootParses.reverse;
  	}
  }

def removeDeterminers(text: String): String={
  if(lemmatiser==null) return null
  val posTagged = lemmatiser.posTag(text);
  
  var newText = ""
  for (tag <- posTagged) {
    tag match{
      case (a,b) => 
        if(b=="DT") return text.replace(a + " ", "").replace("`", "").replace("'", "").trim
        //only first determiner is removed
    }
  }
  text
 
}

def removeDeterminers(possibleParses: List[(List[String], String)], rootNode: Vertex, returnAll: Boolean = false): List[(List[String], String)]={
    var removedParses: List[(List[String], String)] = List()
    var optionalParses: List[(List[String], String)] = List()
    if(lemmatiser==null) return null
    for (g <- possibleParses) {
      g match {
        case (nodeTexts: List[String], edgeText: String) => 
        var newNodes = nodeTexts.toArray;
        for(i <- 0 to nodeTexts.length-1) {
          val nodeText = nodeTexts(i)
          val posTagged = lemmatiser.posTag(nodeText);
          var done = false
          for(tag <- posTagged)
          {
            tag match{
              case (a,b) => 
                if(b=="DT" && done==false) {
                  
                  newNodes(i)=nodeText.replace(a+" ", "").trim.replace("`", "").replace("'", "");
                  val newParse = (newNodes.toList, edgeText)
                  removedParses = newParse::removedParses;
                  done = true //Rmoves only first determiner
                }
              }
          }
        }
        optionalParses = g::optionalParses;
        
      }
      
    }
    if(returnAll) {
      
     return removedParses.reverse++optionalParses.reverse
    }
    else {
      return removedParses.reverse;
    }

}


def getOrCreate(id:String, user:Option[Vertex] = None, textString:String = "", root:Vertex = store.createTextNode("", "")):Vertex={
  if(id != "") {
    try{
      return store.get(id);
    }
    catch{
      case e => val newNode = textToNode(textString, root, user)(0);
      //TextNode(id=ID.usergenerated_id(userID, textString), text=textString);
      return newNode;
    }
  }
  else {
    val newNode = textToNode(textString, root, user)(0);
    return newNode;
  }

}
def nodeExists(id:String):Boolean =
  {
    try{

      val v = store.get(id)
      if(v.id==id) {

        return true
      }
      else {
        return false
      }

    }
    catch{
      case e => return false
    }
  }



}


object SentenceParser {
  def main(args: Array[String]) {
  	  val sentenceParser = new SentenceParser()
      
      val rootNode = sentenceParser.store.createTextNode(namespace="usergenerated/chihchun_chen", text="toad")
      val userNode = sentenceParser.store.createUserNode(id="user/chihchun_chen", username="chihchun_chen", name="Chih-Chun Chen")
  	  val sentence = args.reduceLeft((w1:String, w2:String) => w1 + " " + w2)
      println("From command line with general: " + sentence)
      val responses = sentenceParser.parseSentenceGeneral(sentence, user = Some(userNode))
        for(response <- responses) {
          response match {
            case g: GraphResponse =>
              val parses = g.hypergraphList
              for(parse <- parses) {
                parse match {
                  case (n: List[(Vertex, Option[Vertex])], r: Vertex) =>
                  for(node <- n) {
                    println("Node: " + node._1.id);
                  }
                  println("Rel: " + r.id)
                }

              }
            case r: ResponseType => println(r)

          }
 
      }

	}
}
