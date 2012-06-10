package com.graphbrain.hgdb


trait NodeManagement extends VertexStoreInterface {
  
  def createBrain(name: String, user: UserNode, access:String): String = {
    val id = ID.brainId(name, user.username)
    put(Brain(id=id, name=name, access=access))

    // add brain to user
    put(user.setBrains(user.brains + id))

    // create "brain -> user" relationship
    // make user "brain" node exists
    if (!exists("brain")) {
      put(TextNode("brain", "brain"))
    }
    addrel("brain", Array(user.id, id))

    // create "is -> brain" relationship
    addrel("is", Array(id, "brain"))

    id
  }

  def brainId(vertex: Vertex): String = {
    vertex match {
      case b: Brain => b.id
      case u: UserNode => u.id
      case v: Vertex => {
        val tokens = v.id.split("/")
        if (tokens.size < 3) {
          ""
        }
        else if (tokens(0) == "brain") {
          tokens(0) + "/" + tokens(1) + "/" + tokens(2)
        }
        else if (tokens(0) == "user") {
          tokens(0) + "/" + tokens(1)
        }
        else {
          ""
        }
      }
      case _ => ""
    }
  }

  def brainOwner(brainId: String): String = {
    val tokens = brainId.split("/")
    if (tokens(0) == "brain") {
      "user/" + tokens(1)
    }
    else if (tokens(0) == "user") {
      "user/" + tokens(1)
    }
    else {
      ""
    }
  }

  def createAndConnectVertices(edgeType: String, participants: Array[Vertex]) = {
    for (v <- participants) {
      if (!exists(v.id)) {
        put(v)
      }
    }

    val ids = for (v <- participants) yield v.id
    addrel(edgeType.replace(" ", "_"), ids)
  }

  def removeVertexAndEdges(vertex: Vertex) = {
    var curEdgeList = vertex
    
    // iterate through extra edges
    var extra = 1
    var done = false
    while (!done) {
      // remove each edge
      for (edge <- curEdgeList.edges) {
        val edgeType = Edge.edgeType(edge)
        val participants = Edge.participantIds(edge).toArray
        delrel(edgeType, participants)
      }

      val extraId = VertexStore.extraId(vertex.id, extra)
      if (exists(extraId)) {
        curEdgeList = get(extraId)
        extra += 1
      }
      else {
        done = true
      }
    }

    // remove vertex
    remove(vertex)
  }
}