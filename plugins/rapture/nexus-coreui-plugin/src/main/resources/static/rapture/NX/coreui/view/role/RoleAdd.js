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
/*global Ext, NX*/

/**
 * Add role window.
 *
 * @since 3.0
 */
Ext.define('NX.coreui.view.role.RoleAdd', {
  extend: 'NX.view.AddWindow',
  alias: 'widget.nx-coreui-role-add',
  requires: [
    'NX.Conditions'
  ],

  title: 'Create new role',
  defaultFocus: 'id',

  initComponent: function() {
    var me = this;

    me.items = {
      xtype: 'nx-coreui-role-settings-form',
      api: {
        submit: 'NX.direct.coreui_Role.create'
      },
      settingsFormSuccessMessage: function(data) {
        return 'Role created: ' + data['name'];
      },
      editableCondition: NX.Conditions.isPermitted('security:roles', 'create'),
      editableMarker: 'You do not have permission to create roles',
      source: me.source
    };

    me.callParent(arguments);

    me.down('#id').setReadOnly(false);
  }

});
