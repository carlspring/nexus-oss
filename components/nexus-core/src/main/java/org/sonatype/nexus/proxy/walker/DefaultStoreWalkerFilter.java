/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2014 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.walker;

import org.sonatype.nexus.proxy.item.StorageCollectionItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.uid.IsHiddenAttribute;

public class DefaultStoreWalkerFilter
    implements WalkerFilter
{
  public boolean shouldProcess(WalkerContext context, StorageItem item) {
    return !isHidden(context, item);
  }

  public boolean shouldProcessRecursively(WalkerContext context, StorageCollectionItem coll) {
    return !isHidden(context, coll);
  }

  // ==

  protected boolean isHidden(WalkerContext context, StorageItem item) {
    return item.getRepositoryItemUid().getBooleanAttributeValue(IsHiddenAttribute.class);
  }
}
