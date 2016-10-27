<%include "header.gsp"%>
<header class="header text-center">
  <div class="container">
      <div class="branding">
          <h1 class="doc-title">
              <span aria-hidden="true" class="${content.getOrDefault('microwavetitleicon', 'icon_documents_alt')} icon"></span>
              <a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/index.html">
                Microwave
              </a>
          </h1>
      </div>
  </div><!--//container-->
</header><!--//header-->
<div class="doc-wrapper">
    <div class="container">
        <div id="doc-header" class="doc-header text-center">
            <h1 class="doc-title"><span aria-hidden="true" class="icon icon_lifesaver"></span> ${content.title}</h1>
        </div><!--//doc-header-->

<div class="doc-body">
    <div class="doc-content">
        <div class="content-inner">

<%if (content.body) {%>

<%if (content.containsKey('microwavepdf')) {%>
<div class='btn-toolbar pull-right' style="z-index: 2000;">
  <div class='btn-group'>
      <a class="btn" href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>${content.uri.replace('html', 'pdf')}"><i class="fa fa-file-pdf-o"></i> Download as PDF</a>
  </div>
</div>
<% } %>


            <section class="doc-section">
                ${content.body}
            </section><!--//doc-section-->

<% } %>


        </div><!--//content-inner-->
    </div><!--//doc-content-->

    <div class="doc-sidebar">
        <nav id="doc-nav">
            <ul id="doc-menu" class="nav doc-menu hidden-xs affix-top" data-spy="affix">
                <li><a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/index.html">Home</a></li>
                <li><a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/start.html">Quick Start</a></li>
                <li><a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/components.html">Components</a></li>
                <li><a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/download.html">Download</a></li>
                <li><a href="<%if (content.rootpath) {%>${content.rootpath}<% } else { %><% }%>/community.html">Community</a></li>
            </ul><!--//doc-menu-->
        </nav>
    </div>
</div>

<%include "footer.gsp"%>
