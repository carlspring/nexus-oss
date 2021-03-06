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

package org.sonatype.nexus.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sonatype.nexus.configuration.model.CLocalStorage;
import org.sonatype.nexus.configuration.model.CRemoteStorage;
import org.sonatype.nexus.configuration.model.CRepository;
import org.sonatype.nexus.configuration.model.DefaultCRepository;
import org.sonatype.nexus.proxy.maven.ChecksumPolicy;
import org.sonatype.nexus.proxy.maven.RepositoryPolicy;
import org.sonatype.nexus.proxy.maven.maven2.M2GroupRepository;
import org.sonatype.nexus.proxy.maven.maven2.M2GroupRepositoryConfiguration;
import org.sonatype.nexus.proxy.maven.maven2.M2Repository;
import org.sonatype.nexus.proxy.maven.maven2.M2RepositoryConfiguration;
import org.sonatype.nexus.proxy.repository.GroupRepository;
import org.sonatype.nexus.proxy.repository.Repository;

import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * The Class JettyTestsuiteEnvironment.
 *
 * @author cstamas
 */
public class M2TestsuiteEnvironmentBuilder
    extends AbstractJettyEnvironmentBuilder
{
  public M2TestsuiteEnvironmentBuilder(final String... reposes) {
    this(Arrays.asList(reposes));
  }

  public M2TestsuiteEnvironmentBuilder(final List<String> reposes) {
    super(reposes);
  }

  @Override
  public void buildEnvironment(AbstractProxyTestEnvironment env)
      throws Exception
  {
    List<String> reposes = new ArrayList<String>();
    for (String remoteRepoId : repoIds()) {
      // create proxy for remote
      M2Repository repo = (M2Repository) env.lookup(Repository.class, "maven2");
      CRepository repoConf = new DefaultCRepository();
      repoConf.setProviderRole(Repository.class.getName());
      repoConf.setProviderHint("maven2");
      repoConf.setId(remoteRepoId);
      repoConf.setName(remoteRepoId);
      repoConf.setNotFoundCacheActive(true);

      repoConf.setLocalStorage(new CLocalStorage());
      repoConf.getLocalStorage().setProvider("file");
      repoConf.getLocalStorage().setUrl(
          env
              .getApplicationConfiguration().getWorkingDirectory("proxy/store/" + remoteRepoId).toURI()
              .toURL().toString()
      );

      Xpp3Dom ex = new Xpp3Dom("externalConfiguration");
      repoConf.setExternalConfiguration(ex);
      M2RepositoryConfiguration exConf = new M2RepositoryConfiguration(ex);
      if (remoteRepoId.endsWith("-snapshot")) {
        exConf.setRepositoryPolicy(RepositoryPolicy.SNAPSHOT);
      }
      else {
        exConf.setRepositoryPolicy(RepositoryPolicy.RELEASE);
      }
      exConf.setChecksumPolicy(ChecksumPolicy.STRICT_IF_EXISTS);
      exConf.setRoutingDiscoveryEnabled(false);

      repoConf.setRemoteStorage(new CRemoteStorage());
      repoConf.getRemoteStorage().setProvider(env.getRemoteProviderHintFactory().getDefaultHttpRoleHint());
      repoConf.getRemoteStorage().setUrl(server().getUrl() + "/" + remoteRepoId + "/");

      repo.configure(repoConf);

      // repo.setCacheManager( env.getCacheManager() );
      reposes.add(repo.getId());

      env.getApplicationConfiguration().getConfigurationModel().addRepository(repoConf);

      env.getRepositoryRegistry().addRepository(repo);
    }

    // ading one hosted only
    M2Repository repo = (M2Repository) env.lookup(Repository.class, "maven2");

    CRepository repoConf = new DefaultCRepository();

    repoConf.setProviderRole(Repository.class.getName());
    repoConf.setProviderHint("maven2");
    repoConf.setId("inhouse");

    repoConf.setLocalStorage(new CLocalStorage());
    repoConf.getLocalStorage().setProvider("file");
    repoConf.getLocalStorage().setUrl(
        env.getApplicationConfiguration().getWorkingDirectory("proxy/store/inhouse").toURI().toURL().toString());

    Xpp3Dom exRepo = new Xpp3Dom("externalConfiguration");
    repoConf.setExternalConfiguration(exRepo);
    M2RepositoryConfiguration exRepoConf = new M2RepositoryConfiguration(exRepo);
    exRepoConf.setRepositoryPolicy(RepositoryPolicy.RELEASE);
    exRepoConf.setChecksumPolicy(ChecksumPolicy.STRICT_IF_EXISTS);

    repo.configure(repoConf);

    reposes.add(repo.getId());

    env.getApplicationConfiguration().getConfigurationModel().addRepository(repoConf);

    env.getRepositoryRegistry().addRepository(repo);

    // add a hosted snapshot repo
    M2Repository repoSnapshot = (M2Repository) env.lookup(Repository.class, "maven2");

    CRepository repoSnapshotConf = new DefaultCRepository();

    repoSnapshotConf.setProviderRole(Repository.class.getName());
    repoSnapshotConf.setProviderHint("maven2");
    repoSnapshotConf.setId("inhouse-snapshot");

    repoSnapshotConf.setLocalStorage(new CLocalStorage());
    repoSnapshotConf.getLocalStorage().setProvider("file");
    repoSnapshotConf.getLocalStorage().setUrl(
        env
            .getApplicationConfiguration().getWorkingDirectory("proxy/store/inhouse-snapshot").toURI().toURL()
            .toString()
    );

    Xpp3Dom exSnapRepo = new Xpp3Dom("externalConfiguration");
    repoSnapshotConf.setExternalConfiguration(exSnapRepo);
    M2RepositoryConfiguration exSnapRepoConf = new M2RepositoryConfiguration(exSnapRepo);
    exSnapRepoConf.setRepositoryPolicy(RepositoryPolicy.SNAPSHOT);
    exSnapRepoConf.setChecksumPolicy(ChecksumPolicy.STRICT_IF_EXISTS);

    repoSnapshot.configure(repoSnapshotConf);

    reposes.add(repoSnapshot.getId());

    env.getApplicationConfiguration().getConfigurationModel().addRepository(repoSnapshotConf);

    env.getRepositoryRegistry().addRepository(repoSnapshot);

    // add a group
    M2GroupRepository group = (M2GroupRepository) env.lookup(GroupRepository.class, "maven2");

    CRepository repoGroupConf = new DefaultCRepository();

    repoGroupConf.setProviderRole(GroupRepository.class.getName());
    repoGroupConf.setProviderHint("maven2");
    repoGroupConf.setId("test");

    repoGroupConf.setLocalStorage(new CLocalStorage());
    repoGroupConf.getLocalStorage().setProvider("file");
    repoGroupConf.getLocalStorage().setUrl(
        env.getApplicationConfiguration().getWorkingDirectory("proxy/store/test").toURI().toURL().toString());

    Xpp3Dom exGroupRepo = new Xpp3Dom("externalConfiguration");
    repoGroupConf.setExternalConfiguration(exGroupRepo);
    M2GroupRepositoryConfiguration exGroupRepoConf = new M2GroupRepositoryConfiguration(exGroupRepo);
    exGroupRepoConf.setMemberRepositoryIds(reposes);
    exGroupRepoConf.setMergeMetadata(true);

    group.configure(repoGroupConf);

    env.getApplicationConfiguration().getConfigurationModel().addRepository(repoGroupConf);

    env.getRepositoryRegistry().addRepository(group);
  }
}
