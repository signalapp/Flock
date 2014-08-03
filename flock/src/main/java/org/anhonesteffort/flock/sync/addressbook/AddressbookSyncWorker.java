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

package org.anhonesteffort.flock.sync.addressbook;

import android.content.Context;
import android.content.SyncResult;

import ezvcard.VCard;

import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.sync.AbstractDavSyncWorker;
import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Programmer: rhodey
 */
public class AddressbookSyncWorker extends AbstractDavSyncWorker<VCard> {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.AddressbookSyncWorker";

  protected AddressbookSyncWorker(Context                 context,
                                  SyncResult              result,
                                  LocalContactCollection  localCollection,
                                  HidingCardDavCollection remoteCollection)
  {
    super(context, result, localCollection, remoteCollection);
  }

  @Override
  protected Namespace getNamespace() {
    return CardDavConstants.CARDDAV_NAMESPACE;
  }

  @Override
  protected boolean componentHasUid(VCard component) {
    return component.getUid() != null && component.getUid().getValue() != null;
  }

  @Override
  protected void prePushLocallyCreatedComponent(VCard component) {

  }

}
