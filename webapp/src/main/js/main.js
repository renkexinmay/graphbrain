/**
 * (c) 2012 GraphBrain Ltd. All rigths reserved.
 */

// Entry point functions & global variables
var g;
var context;
var uiMode;
var draggedNode;
var dragging;
var newLink;
var tipVisible;
var lastX;
var lastY;

var initInterface = function() {
    if (error != '') {
        $("#tip").html('<div class="error">' + error + '</div>');
        $("#tip").fadeIn("slow", function(){tipVisible = true;});       
    }

    newLink = false;
    draggedNode = false;
    dragging = false;
    curTargNode = false;
    tipVisible = false;

    $("#nodesDiv").bind("mouseup", (function(e) {
        dragging = false;

        if (uiMode === 'drag') {
            draggedNode = false;
        }
        else { 
            if ((newLink.orig) || (newLink.targ)) {
                if (tipVisible) {
                    $(".tip").fadeOut("slow", function(){tipVisible = false;});
                }
                $('#overlay').fadeIn(80, (function(e) {
                    $('#box').css({visibility:'visible'});
                    if (newLink.orig) {
                        $('#dNode1').html(newLink.orig.text);
                        $('#dNode1_id').val(newLink.orig.id);
                        $('#dNode1').css({display:'block'});
                        $('#dNode1In').css({display:'none'});
                    }
                    else {
                        $('#dNode1').css({display:'none'});
                        $('#dNode1In').css({display:'block'});
                        $('#dNode1_id').val('none');
                    }
                    if (newLink.targ) {
                        $('#dNode2').html(newLink.targ.text);
                        $('#dNode2_id').val(newLink.targ.id);
                        $('#dNode2').css({display:'block'});
                        $('#dNode2In').css({display:'none'});
                    }
                    else {
                        $('#dNode2').css({display:'none'});
                        $('#dNode2In').css({display:'block'});
                        $('#dNode2_id').val('none');
                    }
                    newLink.targ = false;
                }));
            }
            else {
                newLink = false;
                g.drawLinks();
            }
        }
    }));

    $("#nodesDiv").bind("mousedown", function(e) {
        dragging = true;
        lastX = e.pageX;
        lastY = e.pageY;

        if (uiMode === 'drag') {
            return false;
        }
        else {
            newLink = new Link(0, false, false, '...');
            newLink.ox = e.pageX;
            newLink.oy = e.pageY;
            newLink.tx = e.pageX;
            newLink.ty = e.pageY;
            return false;
        }
    });

    $("#nodesDiv").bind("mousemove", (function(e) {
        if (dragging) {
            var deltaX = e.pageX - lastX;
            var deltaY = e.pageY - lastY;
            lastX = e.pageX;
            lastY = e.pageY;
            g.rotateX(-deltaX * 0.0015);
            g.rotateY(deltaY * 0.0015);
            g.updateView();
        }

        if (uiMode === 'drag') {
            if (draggedNode) {
                draggedNode.moveTo(e.pageX, e.pageY);
                dragging = true;
            }
        }
        else {
            if (newLink) {
                newLink.tx = e.pageX;
                newLink.ty = e.pageY;
                g.drawLinks();
                return false;
            }
        }
    }));

    $('#boxclose').click(function(){
        $('#overlay').fadeOut(80, (function(e) {
            $('#box').css({visibility:'hidden'});
            newLink = false;
            g.drawLinks();
        }));
    });

    dragMode();
    $("#dragModeButton").bind("click", dragMode);
    $("#addModeButton").bind("click", addMode);
}

var initGraph = function() {
    g = new Graph();

    // process super nodes and associated nodes
    var i, j;
    for (i = 0; i < snodes.length; i++) {
        var sn = snodes[i];
        var sid = sn['id'];
        var nlist = sn['nodes'];
        
        var snode = new SNode(sid)

        for (j = 0; j < nlist.length; j++) {
            var nid = nlist[j];
            var nod = nodes[nid];
            var text = nod['text'];
            var type = nod['type'];
            var parentID = nod['parent'];
            var node = new Node(nid, text, type, snode);
            snode.nodes[nid] = node;
            g.nodes[nid] = node;

            if ((snode.parent == 'unknown') || (parentID == '')) {
                snode.parent = parentID;
            }
        }
        g.snodes[sid] = snode;
    }   

    // assign root, parents and subNodes
    for (i = 0; i < snodes.length; i++) {
        var sid = snodes[i]['id'];
        var snode = g.snodes[sid];
        var parentID = snode.parent;
        if (parentID == '') {
            g.root = snode;
            snode.parent = false;
        }
        else {
            snode.parent = g.nodes[parentID].snode;
            g.nodes[parentID].snode.subNodes[g.nodes[parentID].snode.subNodes.length] = snode;
        }
    }

    // assign depth and weight
    for (var key in g.snodes) {
        if (g.snodes.hasOwnProperty(key)) {
            var snode = g.snodes[key];
            snode.weight = snode.nodes.size();
            if (snode.parent == '') {
                snode.depth = 0;
            }
            else if (snode.parent == g.root) {
                snode.depth = 1;

                for (var i = 0; i < snode.subNodes.length; i++) {
                    var subNode = snode.subNodes[i];              
                    snode.weight += subNode.nodes.size(); 
                }
            }
            else {
                snode.depth = 2;
            }
        }
    }

    g.genSNodeKeys();

    // process links
    var linkID = 0;
    for (i = 0; i < links.length; i++) {
        var l = links[i];
        var orig = '';
        var targ = '';
        var sorig = '';
        var starg = '';
        if ('orig' in l) {
            orig  = g.nodes[l['orig']];
            sorig = orig.snode;
        }
        else {
            orig = false;
            sorig = g.snodes[l['sorig']];
        }
        if ('targ' in l) {
            targ  = g.nodes[l['targ']];
            starg = targ.snode;
        }
        else {
            targ = false;
            starg  = g.snodes[l['starg']]
        }
        var link = new Link(linkID++, orig, sorig, targ, starg, l['relation']);
        g.links.push(link);
        sorig.links.push(link);
        starg.links.push(link);
    }
    
    var halfWidth = window.innerWidth / 2;
    var halfHeight = window.innerHeight / 2;

    g.placeNodes();
    g.placeLinks();
    g.layout(window.innerWidth, window.innerHeight);
    g.updateView();
}

$(function() {
    initInterface();
    initGraph();
});