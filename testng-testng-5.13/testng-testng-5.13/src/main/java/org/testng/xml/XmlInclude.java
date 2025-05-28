package org.testng.xml;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.testng.reporters.XMLStringBuffer;

import com.google.inject.internal.Lists;

public class XmlInclude {
  private String m_name;
  private List<Integer> m_invocationNumbers = Lists.newArrayList();
  private int m_index;

  public XmlInclude(String n) {
    this(n, Collections.<Integer>emptyList(), 0);
  }

  public XmlInclude(String n, int index) {
    this(n, Collections.<Integer>emptyList(), index);
  }

  public XmlInclude(String n, List<Integer> list, int index) {
    m_name = n;
    m_invocationNumbers = list;
    m_index = index;
  }

  public String getName() {
    return m_name;
  }
  
  public List<Integer> getInvocationNumbers() {
    return m_invocationNumbers;
  }

  public int getIndex() {
    return m_index;
  }

  public String toXml(String indent) {
    XMLStringBuffer xsb = new XMLStringBuffer(indent);
    Properties p = new Properties();
    p.setProperty("name", getName());
    if (getInvocationNumbers().size() > 0) {
      p.setProperty("invocation-numbers", XmlClass.listToString(getInvocationNumbers()).toString());
    }
    xsb.addEmptyElement("include", p);

    return xsb.toXML();
  }
}
