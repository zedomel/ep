$(document).ready(function() {
	$( "#searchForm" ).submit(function( e ) {
		e.preventDefault();
		ajaxSubmitForm();
	});
});

successFn = function(data){
	var viz = d3.select('#visualization'),
		width = +viz.attr('width');
	
	
	for(i = 0; i< data.clusters.length; i++){
		var clId = data.clusters[i].id;
		var clSize = data.clusters[i].size;
		container.append('<h1>Cluster: ' + clId + ' Size: ' + clSize + '</h1>');
		for (j = 0; j < data.clusters[i].documents.length; j++){
			var docId = parseInt(data.clusters[i].documents[j]);
			var doc = data.documents[docId];			
			var score = doc.fields.relevance;
			var authors = doc.fields.authors;
			container.append('<p><b>Score: ' + score + '</b> Title: ' + doc.title + ' Authors: ' + authors + '</p>');
		}
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