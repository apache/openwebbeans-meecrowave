package com.superbiz.servlet.vaadin;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("")
public class HelloVaadinV10 extends Composite<Div> {

  public HelloVaadinV10() {
    final VerticalLayout layout = new VerticalLayout();
    layout
        .add(new Button("click me",
                        event -> layout.add(new Label("clicked again"))
        ));
    //set the main Component
    getContent().add(layout);

  }
}
