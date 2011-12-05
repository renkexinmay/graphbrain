// Node
var Node = function(id, text, type, snode) {
    this.id = id;
    this.text = text;
    this.type = type;
    this.x = 0;
    this.y = 0;
    this.vX = 0;
    this.vY = 0;
    this.subNodes = [];
    this.snode = snode;

    // position in relation to super node
    this.sx = 0;
    this.sy = 0;
}

Node.prototype.updatePos = function() {
    var nodeDiv = $('#' + this.id)
    var offset = nodeDiv.offset();
    this.x = offset.left + this.halfWidth;
    this.y = offset.top + this.halfHeight;
    this.x0 = this.x - this.halfWidth;
    this.y0 = this.y - this.halfHeight;
    this.x1 = this.x + this.halfWidth;
    this.y1 = this.y + this.halfHeight;

    this.sx = this.x - this.snode.x;
    this.sy = this.y - this.snode.y;
}

Node.prototype.estimatePos = function() {
    this.x = this.snode.x + this.sx;
    this.y = this.snode.y + this.sy;

    this.x0 = this.x - this.halfWidth;
    this.y0 = this.y - this.halfHeight;
    this.x1 = this.x + this.halfWidth;
    this.y1 = this.y + this.halfHeight;
    //console.log('nx: ' + this.x + '; ny: ' + this.y);
}

Node.prototype.place = function() {
    var node = document.createElement('div');
    node.setAttribute('class', 'node');
    node.setAttribute('id', this.id);
    if (this.type == 'text') {
        node.innerHTML = '<a href="/node/' + this.id + '" id="' + this.id + '">' + this.text + '</a>';
    }
    else if (this.type == 'image') {
        node.innerHTML = '<a href="/node/' + this.id + '" id="' + this.id + '"><img src="' + this.text + '" width="50px" /></a>';
    }
    var snodeDiv = document.getElementById(this.snode.id);
    snodeDiv.appendChild(node);

    var nodeDiv = $('#' + this.id)
    var width = nodeDiv.outerWidth();
    var height = nodeDiv.outerHeight();
    if (this.type == 'image') {
        height = 55;
    }
    
    this.width = width;
    this.height = height;
    this.halfWidth = width / 2;
    this.halfHeight = height / 2;
   
    this.updatePos();

    /*
    var nodeObj = this;

    $("div#" + this.id).bind("mousedown", function(e) {
        if (uiMode === 'drag') {
            draggedNode = nodeObj;
            return false;
        }
        else {
            newLink = new Link(0, nodeObj, false, '...');
            newLink.tx = e.pageX;
            newLink.ty = e.pageY;
            return false;
        }
    });

    $("div#" + this.id).bind("click", function(e) {
        if (dragging) {
            dragging = false;
            return false;
        }
        else {
            return true;
        }
    });

    $("div#" + this.id).hover(
    function(e) {
        if (newLink) {
            newLink.targ = nodeObj;
        }
    },
    function(e) {});
    */
}