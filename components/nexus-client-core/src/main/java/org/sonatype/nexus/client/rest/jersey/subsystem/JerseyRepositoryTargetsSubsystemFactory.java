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
package org.sonatype.nexus.client.rest.jersey.subsystem;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.client.core.Condition;
import org.sonatype.nexus.client.core.condition.NexusStatusConditions;
import org.sonatype.nexus.client.core.spi.SubsystemFactory;
import org.sonatype.nexus.client.core.subsystem.targets.RepositoryTargets;
import org.sonatype.nexus.client.internal.rest.jersey.subsystem.targets.JerseyRepositoryTargets;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClient;

/**
 * Jersey based {@link org.sonatype.nexus.client.core.subsystem.security.RepositoryTargets} subsystem factory.
 *
 * @since 2.3
 */
@Named
@Singleton
public class JerseyRepositoryTargetsSubsystemFactory
    implements SubsystemFactory<RepositoryTargets, JerseyNexusClient>
{

  @Override
  public Condition availableWhen() {
    return NexusStatusConditions.anyModern();
  }

  @Override
  public Class<RepositoryTargets> getType() {
    return RepositoryTargets.class;
  }

  @Override
  public RepositoryTargets create(JerseyNexusClient nexusClient) {
    return new JerseyRepositoryTargets(nexusClient);
  }

}
