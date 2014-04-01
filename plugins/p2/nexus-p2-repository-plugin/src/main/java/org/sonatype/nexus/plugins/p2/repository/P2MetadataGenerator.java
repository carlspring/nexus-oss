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
package org.sonatype.nexus.plugins.p2.repository;

import org.sonatype.nexus.proxy.item.StorageItem;

public interface P2MetadataGenerator
{

  void addConfiguration(final P2MetadataGeneratorConfiguration configuration);

  void removeConfiguration(final P2MetadataGeneratorConfiguration configuration);

  P2MetadataGeneratorConfiguration getConfiguration(final String repositoryId);

  void generateP2Metadata(StorageItem item);

  void removeP2Metadata(StorageItem item);

  void scanAndRebuild(String repositoryId, String resourceStorePath);

  void scanAndRebuild(String resourceStorePath);

}
