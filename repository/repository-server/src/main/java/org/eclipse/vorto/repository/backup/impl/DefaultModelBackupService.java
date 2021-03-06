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
package org.eclipse.vorto.repository.backup.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.eclipse.vorto.repository.backup.IModelBackupService;
import org.eclipse.vorto.repository.core.IModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultModelBackupService implements IModelBackupService {

	@Autowired
	private Session session;

	@Autowired
	private IModelRepository modelRepository;

	private final Logger LOGGER = LoggerFactory.getLogger(getClass());

	public final String EXCEPTION_MESSAGE_CURRENT_CONTENT = "Not able to create a backup of the current content";
	public final String EXCEPTION_MESSAGE_RESTORABLE_CONTENT = "Not able to restore the imported backup";

	@Override
	public byte[] backup() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		((org.modeshape.jcr.api.Session) session).exportDocumentView("/", baos, false, false);
		baos.close();
		return baos.toByteArray();
	}

	@Override
	public void restore(byte[] restorableContent) throws Exception {
		byte[] currentContent = backup();

		try {
			LOGGER.info("attempting to import backup of current content");
			doRestore(currentContent);
		} catch (Exception invalidCurrentContent) {
			throw new Exception(this.EXCEPTION_MESSAGE_CURRENT_CONTENT);
		}

		removeAll();

		try {
			LOGGER.info("attempting to import imported backup");
			doRestore(restorableContent);
		} catch (Exception invalidContent) {
			doRestore(currentContent);
			throw new Exception(this.EXCEPTION_MESSAGE_RESTORABLE_CONTENT);
		}
	}

	private void doRestore(byte[] backup) throws AccessDeniedException, VersionException, PathNotFoundException,
			ItemExistsException, ConstraintViolationException, InvalidSerializedDataException, LockException,
			IOException, RepositoryException {
		((org.modeshape.jcr.api.Session) session).getWorkspace().importXML("/", new ByteArrayInputStream(backup),
				ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);
		LOGGER.info("created backup succesfully");
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public IModelRepository getModelRepository() {
		return modelRepository;
	}

	public void setModelRepository(IModelRepository modelRepository) {
		this.modelRepository = modelRepository;
	}

	private void removeAll() throws Exception {
		NodeIterator iter = ((org.modeshape.jcr.api.Session) session).getRootNode().getNodes();
		while(iter.hasNext()) {
			Node node = iter.nextNode();
			if (!node.getName().equals("jcr:system")) {
				((org.modeshape.jcr.api.Session) session).removeItem(node.getPath());
			}
		}
		((org.modeshape.jcr.api.Session) session).save();
	}
}
