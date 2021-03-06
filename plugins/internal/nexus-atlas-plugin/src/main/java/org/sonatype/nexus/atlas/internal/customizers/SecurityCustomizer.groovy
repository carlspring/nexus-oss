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
package org.sonatype.nexus.atlas.internal.customizers

import org.sonatype.nexus.configuration.application.ApplicationDirectories
import org.sonatype.nexus.supportzip.GeneratedContentSourceSupport
import org.sonatype.nexus.supportzip.SupportBundle
import org.sonatype.nexus.supportzip.SupportBundleCustomizer
import org.sonatype.security.model.source.SecurityModelConfigurationSource
import org.sonatype.sisu.goodies.common.ComponentSupport

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import static com.google.common.base.Preconditions.checkNotNull
import static org.sonatype.nexus.supportzip.SupportBundle.ContentSource.Priority.HIGH
import static org.sonatype.nexus.supportzip.SupportBundle.ContentSource.Type.SECURITY

/**
 * Adds system security files to support bundle.
 *
 * @since 2.7
 */
@Named
@Singleton
class SecurityCustomizer
    extends ComponentSupport
    implements SupportBundleCustomizer
{
  private final ApplicationDirectories applicationDirectories

  private final SecurityModelConfigurationSource securityModelConfigurationSource

  @Inject
  SecurityCustomizer(final ApplicationDirectories applicationDirectories,
                     final SecurityModelConfigurationSource securityModelConfigurationSource)
  {
    this.applicationDirectories = checkNotNull(applicationDirectories)
    this.securityModelConfigurationSource = checkNotNull(securityModelConfigurationSource)
  }

  @Override
  void customize(final SupportBundle supportBundle) {
    // security.xml
    supportBundle << new SecurityXmlContentSource()

    // security-configuration.xml
    supportBundle << new SecurityConfigurationXmlContentSource()
  }

  /**
   * Source for obfuscated security.xml
   */
  @SuppressWarnings("UnnecessaryQualifiedReference")
  private class SecurityXmlContentSource
      extends GeneratedContentSourceSupport
  {
    SecurityXmlContentSource() {
      super(SECURITY, 'work/etc/security.xml', HIGH)
    }

    @Override
    protected void generate(final File file) {
      def source = new File(applicationDirectories.workDirectory, 'etc/security.xml')
      if (!source.exists()) {
        log.debug 'Skipping non-existent file: {}', source
        return
      }

      log.debug 'Reading: {}', source
      source.withInputStream { input ->
        def model = new org.sonatype.security.model.io.xpp3.SecurityConfigurationXpp3Reader().read(input)

        // obfuscate sensitive content
        for (user in model?.users) {
          user.password = PASSWORD_TOKEN
          user.email = EMAIL_TOKEN
        }

        file.withOutputStream { output ->
          new org.sonatype.security.model.io.xpp3.SecurityConfigurationXpp3Writer().write(output, model)
        }
      }
    }
  }

  /**
   * Source for obfuscated security-configuration.xml
   */
  @SuppressWarnings("UnnecessaryQualifiedReference")
  private class SecurityConfigurationXmlContentSource
      extends GeneratedContentSourceSupport
  {
    SecurityConfigurationXmlContentSource() {
      super(SECURITY, 'work/etc/security-configuration.xml', HIGH)
    }

    @Override
    protected void generate(final File file) {
      def source = new File(applicationDirectories.workDirectory, 'etc/security-configuration.xml')
      if (!source.exists()) {
        log.debug 'Skipping non-existent file: {}', source
        return
      }

      log.debug 'Reading: {}', source
      source.withInputStream { input ->
        def model = new org.sonatype.security.configuration.model.io.xpp3.SecurityConfigurationXpp3Reader().read(input)

        // obfuscate sensitive content
        model.anonymousPassword = PASSWORD_TOKEN

        file.withOutputStream { output ->
          new org.sonatype.security.configuration.model.io.xpp3.SecurityConfigurationXpp3Writer().write(output, model)
        }
      }
    }
  }
}
