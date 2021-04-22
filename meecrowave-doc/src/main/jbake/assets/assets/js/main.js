$(document).ready(function() {
	
	/* ===== Affix Sidebar ===== */
	/* Ref: http://getbootstrap.com/javascript/#affix-examples */

    	
	$('#doc-menu').affix({
        offset: {
            top: ($('#header').outerHeight(true) + $('#doc-header').outerHeight(true)) + 45,
            bottom: ($('#footer').outerHeight(true) + $('#promo-block').outerHeight(true)) + 75
        }
    });
    
    /* Hack related to: https://github.com/twbs/bootstrap/issues/10236 */
    $(window).on('load resize', function() {
        $(window).trigger('scroll'); 
    });

    /* Activate scrollspy menu */
    $('body').scrollspy({target: '#doc-nav', offset: 100});

    /*
    // Smooth scrolling
	$('a.scrollto').on('click', function(e){
        //store hash
        var target = this.hash;    
        e.preventDefault();
		$('body').scrollTo(target, 800, {offset: 0, 'axis':'y'});
		
	});
	*/
	
    
    /* ======= jQuery Responsive equal heights plugin ======= */
    /* Ref: https://github.com/liabru/jquery-match-height */
    
     $('#cards-wrapper .item-inner').matchHeight();
     $('#showcase .card').matchHeight();
     
    /* Bootstrap lightbox */
    /* Ref: http://ashleydw.github.io/lightbox/ */

    $(document).delegate('*[data-toggle="lightbox"]', 'click', function(e) {
        e.preventDefault();
        $(this).ekkoLightbox();
    });    

    hljs.initHighlightingOnLoad();

    // set admonitionblock custom theme
    // drop titles from <i> to not pollute the ui with pointless text
    function setAdmonitionStyle(item, color) {
      var i = $(item);
      i.css('border-left', '1.5px solid ' + color);
      i.css('padding-left', '2rem');
      i.css('background-color', color + '10');
      i.css('color', color);
    }
    $('div.admonitionblock td.icon > i.fa').each(function (idx, item) {
      item.title = '';
  
      var jItem = $(item);
      jItem.addClass('fa-lg');
      var content = jItem.parent().parent().find('td.content');
      if (jItem.hasClass('icon-important')) {
          setAdmonitionStyle(content, '#e96065');
      } else if (jItem.hasClass('icon-note')) {
          setAdmonitionStyle(content, '#0675c1');
      } else if (jItem.hasClass('icon-warning')) {
          setAdmonitionStyle(content, '#ffc300');
      } else {
          setAdmonitionStyle(content, '#6ec01e');
      }
    });
});