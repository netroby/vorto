/**
 * Copyright (c) 2015-2016 Bosch Software Innovations GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Bosch Software Innovations GmbH - Please refer to git log
 */
package org.eclipse.vorto.codegen.gateway.templates

import java.util.Arrays
import java.util.HashSet
import java.util.Set
import org.eclipse.vorto.codegen.api.GeneratorServiceInfo
import org.eclipse.vorto.server.commons.IGeneratorConfigUITemplate

class WebUIConfigTemplate implements IGeneratorConfigUITemplate {
	
	private static final Set<String> KEYS = new HashSet(Arrays.asList("persistence","swagger","boschcloud"));
	
	override getContent(GeneratorServiceInfo info) {
		'''
		<div class="form-group">
			<div class="col-sm-6">
				<div class="box box-primary">
			    	<div class="box-header with-border">
			      		<h3 class="box-title">Features</h3>
			      		<span class="label label-primary pull-right"><i class="fa fa-dashboard"></i></span>
			    	</div><!-- /.box-header -->
					<div class="box-body">
						<p><input type="checkbox" checked disabled="disabled">&nbsp;Data Visualization</p>
						<p><input type="checkbox" ng-model="configParams.persistence">&nbsp;(Historical) Data Persistence</p>
						<p><input type="checkbox" ng-model="configParams.swagger">&nbsp;Swagger Documentation</p>
					</div><!-- /.box-body -->
			  	</div><!-- /.box -->
			</div>
			<div class="col-sm-6">
				<div class="box box-primary">
			    	<div class="box-header with-border">
			      		<h3 class="box-title">IoT Platform Data Integration</h3>
			      		<span class="label label-primary pull-right"><i class="fa fa-dashboard"></i></span>
			    	</div><!-- /.box-header -->
					<div class="box-body">
						<p><input type="checkbox" ng-model="configParams.boschcloud">&nbsp;Bosch IoT Suite Integrator</p>
					</div><!-- /.box-body -->
			  	</div><!-- /.box -->
			</div>
		</div>
		'''
	}
	
	override getKeys() {
		return KEYS;
	}
}