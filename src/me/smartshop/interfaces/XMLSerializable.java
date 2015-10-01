package me.smartshop.interfaces;

import java.io.IOException;

import org.w3c.dom.Element;
import org.xmlpull.v1.XmlSerializer;

public interface XMLSerializable {
	public void serialize(XmlSerializer serializer)  throws RuntimeException, IOException;
	public void deserialize(Element e);
}
