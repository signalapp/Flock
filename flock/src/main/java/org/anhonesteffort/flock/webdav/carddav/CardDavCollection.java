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

package org.anhonesteffort.flock.webdav.carddav;

import com.google.common.base.Optional;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.ProductId;

import org.anhonesteffort.flock.webdav.AbstractDavComponentCollection;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.PrincipalMatchReport;
import org.apache.jackrabbit.webdav.version.report.ReportType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class CardDavCollection extends AbstractDavComponentCollection<VCard> implements DavContactCollection {

  protected CardDavCollection(CardDavStore cardDavStore, String path) {
    super(cardDavStore, path);
  }

  protected CardDavCollection(CardDavStore cardDavStore, String path, DavPropertySet properties) {
    super(cardDavStore, path, properties);
    this.properties = properties;
  }

  @Override
  protected String getComponentPathFromUid(String uid) {
    return getPath().concat(uid.concat(CardDavConstants.VCARD_FILE_EXTENSION));
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet addressbookProps = super.getPropertyNamesForFetch();

    addressbookProps.add(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_DESCRIPTION);
    addressbookProps.add(CardDavConstants.PROPERTY_NAME_SUPPORTED_ADDRESS_DATA);
    addressbookProps.add(CardDavConstants.PROPERTY_NAME_MAX_RESOURCE_SIZE);

    addressbookProps.add(DavPropertyName.DISPLAYNAME);
    addressbookProps.add(SecurityConstants.OWNER);

    return addressbookProps;
  }

  @Override
  protected ReportType getQueryReportType() {
    return ReportType.register(CardDavConstants.PROPERTY_ADDRESSBOOK_QUERY,
        CardDavConstants.CARDDAV_NAMESPACE,
        PrincipalMatchReport.class);
  }

  @Override
  protected ReportType getMultiGetReportType() {
    return ReportType.register(CardDavConstants.PROPERTY_ADDRESSBOOK_MULTIGET,
        CardDavConstants.CARDDAV_NAMESPACE,
        PrincipalMatchReport.class);
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForReports() {
    DavPropertyNameSet addressbookProperties = new DavPropertyNameSet();
    addressbookProperties.add(CardDavConstants.PROPERTY_NAME_ADDRESS_DATA);
    addressbookProperties.add(DavPropertyName.GETETAG);
    return addressbookProperties;
  }

  @Override
  public Optional<String> getDescription() throws PropertyParseException {
    return getProperty(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_DESCRIPTION, String.class);
  }

  @Override
  public void setDescription(String description) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_DESCRIPTION, description));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public Optional<Long> getMaxResourceSize() throws PropertyParseException {
    return getProperty(CardDavConstants.PROPERTY_NAME_MAX_RESOURCE_SIZE, Long.class);
  }

  @Override
  protected MultiStatusResult<VCard> getComponentsFromMultiStatus(MultiStatusResponse[] msResponses) {
    List<ComponentETagPair<VCard>>  vCards     = new LinkedList<ComponentETagPair<VCard>>();
    List<InvalidComponentException> exceptions = new LinkedList<InvalidComponentException>();

    for (MultiStatusResponse response : msResponses) {
      VCard          vCard       = null;
      String         eTag        = null;
      DavPropertySet propertySet = response.getProperties(DavServletResponse.SC_OK);

      if (propertySet.get(CardDavConstants.PROPERTY_NAME_ADDRESS_DATA) != null) {
        String addressData = (String) propertySet.get(CardDavConstants.PROPERTY_NAME_ADDRESS_DATA).getValue();
        try {

          vCard = Ezvcard.parse(addressData).first();

        } catch (RuntimeException e) {
          exceptions.add(
              new InvalidComponentException("caught exception while parsing vcard from multi-status response",
                                            CardDavConstants.CARDDAV_NAMESPACE, getPath(), e)
          );
        }
      }

      if (propertySet.get(DavPropertyName.GETETAG) != null)
        eTag = (String) propertySet.get(DavPropertyName.GETETAG).getValue();

      if (vCard != null)
        vCards.add(new ComponentETagPair<VCard>(vCard, Optional.fromNullable(eTag)));
    }

    return new MultiStatusResult<VCard>(vCards, exceptions);
  }

  @Override
  protected void putComponentToServer(VCard vCard, Optional<String> ifMatchETag)
      throws IOException, DavException, InvalidComponentException
  {
    if (vCard.getUid() == null || vCard.getUid().getValue() == null)
      throw new InvalidComponentException("Cannot put a VCard to server without UID!",
                                          CardDavConstants.CARDDAV_NAMESPACE, getPath());

    vCard.getProperties().add(new ProductId(((CardDavStore)getStore()).getProductId()));

    String    vCardUid  = vCard.getUid().getValue();
    PutMethod putMethod = new PutMethod(getComponentPathFromUid(vCardUid));

    if (ifMatchETag.isPresent())
      putMethod.addRequestHeader("If-Match", ifMatchETag.get()); // TODO: constant for this.
    else
      putMethod.addRequestHeader("If-None-Match", "*"); // TODO: constant for this.

    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    Ezvcard.write(vCard).go(byteStream);

    putMethod.setRequestEntity(new ByteArrayRequestEntity(byteStream.toByteArray(),
                                                          CardDavConstants.HEADER_CONTENT_TYPE_VCARD));

    byteStream = new ByteArrayOutputStream();
    Ezvcard.write(vCard).go(byteStream);

    try {

      client.execute(putMethod);
      int status = putMethod.getStatusCode();

      if (status == DavServletResponse.SC_REQUEST_ENTITY_TOO_LARGE ||
          status == DavServletResponse.SC_FORBIDDEN)
      {
        throw new InvalidComponentException("Put method returned bad status " + status,
                                            CardDavConstants.CARDDAV_NAMESPACE, getPath(), vCardUid);
      }

      if (putMethod.getStatusCode() < DavServletResponse.SC_OK ||
          putMethod.getStatusCode() > DavServletResponse.SC_NO_CONTENT)
      {
        throw new DavException(putMethod.getStatusCode(), putMethod.getStatusText());
      }

    } finally {
      putMethod.releaseConnection();
    }
  }
}
