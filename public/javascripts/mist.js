$(document).ready(function() {
	$( "#searchForm" ).submit(function( e ) {
		e.preventDefault();
		ajaxSubmitForm();
	});
});

successFn = function(data){
	var svg = d3.select("svg"),
    width = +svg.attr("width"),
    height = +svg.attr("height");
	
	
	//Clear previous visualization
	svg.selectAll('g').remove();
	svg.selectAll('defs').remove();
	
	var padding = 1.5, // separation between same-color nodes
	clusterPadding = 6, // separation between different-color nodes
	maxRadius = width * 0.05;
	minRadius = width * 0.005;

	var n = data.documents.length, // total number of documents
	m = data.clusters.length; // number of distinct clusters

	var color = d3.scaleSequential(d3.interpolateRainbow)
		.domain([0, m]);
		
	var minRev = data.documents[0].fields.relevance,
		maxRev = minRev;
	
	var i = 0;
	for(i = 1; i < n; i++){
		var rev = data.documents[i].fields.relevance;
		if (rev < minRev)
			minRev = rev;
		if (rev > maxRev)
			maxRev = rev;
	}
	
	var radiusInterpolator = d3.scaleLinear()
		.domain([minRev, maxRev])
		.range([minRadius, maxRadius])
		.interpolate(d3.interpolateRound);
	
	// The largest node for each cluster.
	var clusters = new Array(m);
	var links = [];
	
	var nodes = $.map(data.clusters, function(cluster, index){
		var id = cluster.id;
		if (cluster.attributes.coordinates != undefined){
			var coords = cluster.attributes.coordinates;
			var neighbors = cluster.attributes.neighbors;
			return $.map( cluster.documents, function(docId, docIndex){
				var doc = data.documents[parseInt(docId)];
				var r = radiusInterpolator(doc.fields.relevance),
				d = {
					cluster: id,
					radius: r,
					x: coords[docIndex][0] + Math.random() + width/2,
					y: coords[docIndex][1] + Math.random() + height/2,
					nb: neighbors[docIndex],
					title: doc.title,
					url: doc.url,
					fields: doc.fields,
				};
				
				if ( doc.fields.references ){
					var i;
					var references = doc.fields.references;
					for(i = 0; i < references.length; i++){
						links.push({	
							source: doc.fields.id,
							target: references[i]
						});
					}
				}
				
				if (!clusters[id] || (r > clusters[id].radius)) clusters[id] = d;
				return d;
			});
		}
	});

	var linkForce = d3.forceLink()
		.id(function(d){
			return d.fields.id;
		})
		.strength(0)
		.distance(0);
	
	// Attraction force (MIST)
	forceAttraction = forceAttractive();
	
	var simulation = d3.forceSimulation()
    	.force("charge", d3.forceManyBody().strength(0))
    	//.force('collision', d3.forceCollide(function(d){
    	//	return d.radius + 0.1;
    	//}))
    	.force('pos_x', d3.forceX(width/2).strength(.02))
    	.force('pos_y', d3.forceY(height/2).strength(.02))
    	.force("link", linkForce)
    	.alpha(0.01);

	// Tooltip on mouse over
	var tip = d3.select("body").append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);
	
	// Per-type markers, as they don't inherit styles.
	svg.append("defs").selectAll("marker")
	    .data(["link"])
	  .enter().append("marker")
	    .attr("id", function(d) { return d; })
	    .attr("viewBox", "0 -5 10 10")
	    .attr("refX", 8)
	    .attr("refY", 0)
	    .attr("markerWidth", 6)
	    .attr("markerHeight", 6)
	    .attr("orient", "auto")
	    .append("path")
	    .attr("d", "M0,-5L10,0L0,5");
	
	var shape = 'circle';
	var node = svg.append('g')
		.selectAll(shape)
		.data(nodes)
		.enter().append(shape)
		.style("fill", function(d) { return color(d.cluster); })
		.call(d3.drag()
			.on("start", dragstarted)
			.on("drag", dragged)
			.on("end", dragended))
		.on("mouseover", function(d) {
			
			tip.transition()
				.duration(200)
				.style("opacity", 0.9)
				.style("display", "block");
			
			var tipHtml = '<a href="' + d.url + '" target="_blank">';
			if (d.fields.full_title)
				tipHtml += "<p><strong>" + d.fields.full_title + "</strong></p>";
			else
				tipHtml += "<p><strong>" + d.title + "</strong></p>";
			tipHtml += "</a>";
			if ( d.fields.authors )
				tipHtml += "<p>" + d.fields.authors + "</p>";
			tipHtml += "<p>";
			if( d.fields.conference )
				tipHtml += d.fields.conference.toUpperCase();
			else if ( d.fields.journal )
				tipHtml += d.fields.journal.toUpperCase();
			if ( d.fields.year )
				tipHtml +=  ", " + d.fields.year;
			tipHtml += "</p>";
			tip.html(tipHtml)
				.style("left", (d3.event.pageX + d.radius + 2) + "px")
		        .style("top", (d3.event.pageY + d.radius + 2) + "px");
			d3.select(this).style("stroke-opacity", 1);
		})
		.on("mouseout", function(d) {

			d3.select(this).style("stroke-opacity", 0);
		    // User has moved off the heat-map and onto the tool-tip
		    tip.on("mouseover", function (t) {
		        tip.on("mouseleave", function (t) {
		            tip.transition().duration(500)
		            .style("opacity", 0)
		            .style("display", "none");
		            
		        });
		    });
		 })
		 .on("click", function(d){
			 var i;
			 var paths = d3.selectAll('path.link').nodes();
			 for(i = 0; i < links.length; i++){
				 if (links[i].source == d){
					 var l = d3.select(paths[links[i].index]);
					 l.classed('active', !l.classed('active'));
				 }
			 }
		 });
	
	var path = svg.append("g")
	.selectAll("path")
	.data(links)
	.enter().append("path")
    .attr("class", "link")
    .call(d3.drag()
		.on("start", dragstarted)
		.on("drag", dragged)
		.on("end", dragended))
    .attr("marker-end", function(d) { return "url(#link)"; });
	
	node.transition()
	.duration(750)
	.delay(function(d, i) { return i * 5; })
	.attrTween("r", function(d) {
		var i = d3.interpolate(0, d.radius);
		return function(t) { return d.radius = i(t); };
	});
	
	simulation
    .nodes(nodes)
    .on("tick", tick);
	
	linkForce.links(links);
	
	function linkArc(d) {
	  var dx = d.target.x - d.source.x,
	      dy = d.target.y - d.source.y,
	      dr = Math.sqrt(dx * dx + dy * dy);
	  return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr + " 0 0,1 " + d.target.x + "," + d.target.y;
	}

	function transform(d) {
	  return "translate(" + d.x + "," + d.y + ")";
	}
	  
	function dragstarted(d) {
		if (!d3.event.active) simulation.alphaTarget(0.3).restart();
		d.fx = d.x;
		d.fy = d.y;
	}

	function dragged(d) {
		d.fx = d3.event.x;
		d.fy = d3.event.y;
	}

	function dragended(d) {
		if (!d3.event.active) simulation.alphaTarget(0);
		d.fx = null;
		d.fy = null;
	}

	function tick(e) {
		node
		.each(cluster( 10 * this.alpha() * this.alpha() ))
		.each(collide(0.5))
		.attr("cx", function(d) { return d.x; })
		.attr("cy", function(d) { return d.y; });
		
		path.attr("d", linkArc);
	}
	
	// Resolves collisions between d and all other circles.
	function collide(alpha) {
	  var quadtree = d3.quadtree()
	  	.extent([[0, 0], [width, height]])
	  	.x(function(d){return d.x;})
	  	.y(function(d){return d.y})
	  	.addAll(nodes);
	  return function(d) {
	    var r = d.radius + maxRadius + Math.max(padding, clusterPadding),
	        nx1 = d.x - r,
	        nx2 = d.x + r,
	        ny1 = d.y - r,
	        ny2 = d.y + r;
	    quadtree.visit(function(node, x1, y1, x2, y2) {
	    	if (!node.length ){
	    		do{
	    			if (node.data !== d) {
	        			var x = d.x - node.data.x,
	        				y = d.y - node.data.y,
	        				l = Math.sqrt(x * x + y * y),
	        				r = d.radius + node.data.radius + (d.cluster === node.data.cluster ? padding : clusterPadding);
	        			if (l < r) {
	        				l = (l - r) / l * alpha;
	        				d.x -= x *= l;
	        				d.y -= y *= l;
	        				node.data.x += x;
	        				node.data.y += y;
	        			}
	      			}
	    		} while (node = node.next);
	    	}
	    	return x1 > nx2 || x2 < nx1 || y1 > ny2 || y2 < ny1;
	    });
	  };
	}

//	Move d to be adjacent to the cluster node.
	function cluster(alpha) {
		return function(d) {
			var cluster = clusters[d.cluster];
			if (cluster === d) return;
			var x = d.x - cluster.x,
				y = d.y - cluster.y,
				l = Math.sqrt(x * x + y * y),
				r = d.radius + cluster.radius;
			if (l != r) {
				l = (l - r) / l * alpha;
				d.x -= x *= l;
				d.y -= y *= l;
				cluster.x += x;
				cluster.y += y;
			}
		};
	}
}

errorFn = function(err){
	console.debug("Error:");
	console.debug(err);
}

function ajaxSubmitForm(){
	var t = $("#term").val();
	var r = jsRoutes.controllers.HomeController.search(t);
	console.debug(r.url);
	console.debug(r.type);
	$.ajax({url: r.url, type: r.type, success: successFn, error: errorFn, dataType: "json"});
}