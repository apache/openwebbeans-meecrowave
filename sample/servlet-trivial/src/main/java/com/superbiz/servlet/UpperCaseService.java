package com.superbiz.servlet;

import javax.enterprise.context.Dependent;

@Dependent
public class UpperCaseService{

  public String upperCase(String txt) {
    return txt.toUpperCase();
  }
}
