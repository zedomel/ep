$(document).ready(function() {
	$( "#searchForm" ).submit(function( e ) {
		e.preventDefault();
		ajaxSubmitForm();
	});
});

successFn = function(data){
	var container = $("#results");
	container.empty();
	for(i = 0; i< data.length; i++){
		var score = data[i].score.toFixed(3);
		container.append('<p><b>Score: ' + score + '</b> Title: ' + data[i].title + ' Authors: ' + data[i].authors + '</p>');
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