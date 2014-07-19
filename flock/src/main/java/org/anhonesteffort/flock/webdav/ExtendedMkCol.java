/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Programmer: rhodey
 */
public class ExtendedMkCol implements XmlSerializable {

  public static final String XML_MKCOL = "mkcol";

  private final DavPropertySet properties;

  public ExtendedMkCol(DavPropertySet properties) {
    this.properties = properties;
  }

  public DavPropertySet getProperties() {
    return properties;
  }

  public Element toXml(Document document) {
    Element mkCol = DomUtil.createElement(document, XML_MKCOL, DavConstants.NAMESPACE);
    Element set   = DomUtil.createElement(document, DavConstants.XML_SET, DavConstants.NAMESPACE);

    set.appendChild(properties.toXml(document));
    mkCol.appendChild(set);

    return mkCol;
  }
}
