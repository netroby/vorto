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
package org.eclipse.vorto.repository.core.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.jcr.Binary;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.eclipse.vorto.repository.account.impl.IUserRepository;
import org.eclipse.vorto.repository.api.ModelId;
import org.eclipse.vorto.repository.api.ModelInfo;
import org.eclipse.vorto.repository.api.ModelType;
import org.eclipse.vorto.repository.api.attachment.Attachment;
import org.eclipse.vorto.repository.api.attachment.Tag;
import org.eclipse.vorto.repository.api.exception.ModelNotFoundException;
import org.eclipse.vorto.repository.core.AttachmentException;
import org.eclipse.vorto.repository.core.FatalModelRepositoryException;
import org.eclipse.vorto.repository.core.FileContent;
import org.eclipse.vorto.repository.core.IModelRepository;
import org.eclipse.vorto.repository.core.IUserContext;
import org.eclipse.vorto.repository.core.ModelFileContent;
import org.eclipse.vorto.repository.core.ModelReferentialIntegrityException;
import org.eclipse.vorto.repository.core.ModelResource;
import org.eclipse.vorto.repository.core.impl.parser.ModelParserFactory;
import org.eclipse.vorto.repository.core.impl.utils.ModelIdHelper;
import org.eclipse.vorto.repository.core.impl.utils.ModelReferencesHelper;
import org.eclipse.vorto.repository.core.impl.utils.ModelSearchUtil;
import org.eclipse.vorto.repository.core.impl.validation.AttachmentValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of the repository using JCR
 * 
 * @author Alexander Edelmann
 *
 */
@Service
public class JcrModelRepository implements IModelRepository {

	private static final String FILE_NODES = "*.type | *.fbmodel | *.infomodel | *.mapping ";

	@Autowired
	private Session session;

	@Autowired
	private IUserRepository userRepository;

	@Autowired
	private ModelSearchUtil modelSearchUtil;

	private static Logger logger = Logger.getLogger(JcrModelRepository.class);

	@Autowired
	private AttachmentValidator attachmentValidator;

	@Override
	public List<ModelInfo> search(final String expression) {
		String queryExpression = expression;
		if (queryExpression == null || queryExpression.isEmpty()) {
			queryExpression = "*";
		}
		try {
			List<ModelInfo> modelResources = new ArrayList<>();

			Query query = modelSearchUtil.createQueryFromExpression(session, queryExpression);

			logger.debug("Searching repository with expression " + query.getStatement());
			QueryResult result = query.execute();
			RowIterator rowIterator = result.getRows();
			while (rowIterator.hasNext()) {
				Row row = rowIterator.nextRow();
				Node currentNode = row.getNode();
				if (currentNode.hasProperty("vorto:type")) {
					try {
						modelResources.add(createMinimalModelInfo(currentNode));
					} catch (Exception ex) {
						logger.debug("Error while converting node to a ModelInfo", ex);
					}
				}
			}

			return modelResources;
		} catch (RepositoryException e) {
			throw new RuntimeException("Could not create query manager", e);
		}
	}

	private ModelInfo createMinimalModelInfo(Node node) throws RepositoryException {

		ModelInfo resource = new ModelInfo(ModelIdHelper.fromPath(node.getParent().getPath()),
				node.getProperty("vorto:type").getString());
		resource.setDescription(node.getProperty("vorto:description").getString());
		resource.setDisplayName(node.getProperty("vorto:displayname").getString());
		resource.setCreationDate(node.getProperty("jcr:created").getDate().getTime());
		if (node.hasProperty("jcr:lastModified")) {
			resource.setModificationDate(node.getProperty("jcr:lastModified").getDate().getTime());
		}
		if (node.hasProperty("vorto:state")) {
			resource.setState(node.getProperty("vorto:state").getString());
		}
		if (node.hasProperty("vorto:author")) {
			resource.setAuthor(node.getProperty("vorto:author").getString());
		}

		NodeIterator imageNodeIterator = node.getParent().getNodes("img.png*");
		if (imageNodeIterator.hasNext()) {
			resource.setHasImage(true);
		}

		return resource;
	}

	private ModelInfo createModelResource(Node node) throws RepositoryException {
		ModelInfo resource = createMinimalModelInfo(node);
		resource.setFileName(node.getName());

		if (node.hasProperty("vorto:references")) {
			Value[] referenceValues = null;
			try {
				referenceValues = node.getProperty("vorto:references").getValues();
			} catch (Exception ex) {
				referenceValues = new Value[] { node.getProperty("vorto:references").getValue() };
			}

			if (referenceValues != null) {
				ModelReferencesHelper referenceHelper = new ModelReferencesHelper();
				for (Value referValue : referenceValues) {
					String nodeUuid = referValue.getString();
					Node referencedNode = session.getNodeByIdentifier(nodeUuid);
					referenceHelper.addModelReference(
							ModelIdHelper.fromPath(referencedNode.getParent().getPath()).getPrettyFormat());
				}
				resource.setReferences(referenceHelper.getReferences());
			}
		}

		PropertyIterator propIter = node.getReferences();
		while (propIter.hasNext()) {
			Property prop = propIter.nextProperty();
			Node referencedByFileNode = prop.getParent();
			final ModelId referencedById = ModelIdHelper.fromPath(referencedByFileNode.getParent().getPath());
			resource.getReferencedBy().add(referencedById);

			if (referencedByFileNode.getName().endsWith(ModelType.Mapping.getExtension())) {
				ModelResource emfResource = getEMFResource(referencedById);
				resource.addPlatformMapping(emfResource.getTargetPlatform(), referencedById);
			}
		}

		return resource;
	}

	@Override
	public ModelFileContent getModelContent(ModelId modelId) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node folderNode = session.getNode(modelIdHelper.getFullPath());
			Node fileNode = (Node) folderNode.getNodes(FILE_NODES).next();
			Node fileItem = (Node) fileNode.getPrimaryItem();
			InputStream is = fileItem.getProperty("jcr:data").getBinary().getStream();

			final String fileContent = IOUtils.toString(is);
			ModelResource resource = (ModelResource) ModelParserFactory.getParser(fileNode.getName())
					.parse(IOUtils.toInputStream(fileContent));
			return new ModelFileContent(resource.getModel(), fileNode.getName(), fileContent.getBytes());

		} catch (PathNotFoundException e) {
			throw new ModelNotFoundException("Could not find model with the given model id", e);
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	private Node createNodeForModelId(ModelId id) throws RepositoryException {
		ModelIdHelper modelIdHelper = new ModelIdHelper(id);

		StringBuilder pathBuilder = new StringBuilder();
		Iterator<String> modelIdIterator = modelIdHelper.iterator();
		Node rootNode = session.getRootNode();
		while (modelIdIterator.hasNext()) {
			String nextPathFragment = modelIdIterator.next();
			pathBuilder.append(nextPathFragment).append("/");
			try {
				rootNode.getNode(pathBuilder.toString());
			} catch (PathNotFoundException pathNotFound) {
				Node addedNode = rootNode.addNode(pathBuilder.toString(), "nt:folder");
				addedNode.setPrimaryType("nt:folder");
			}
		}

		return rootNode.getNode(modelIdHelper.getFullPath().substring(1));
	}

	public ModelInfo save(ModelId modelId, byte[] content, String fileName, IUserContext userContext) {
		Objects.requireNonNull(content);
		Objects.requireNonNull(modelId);

		logger.info("Saving " + modelId.toString() + " as " + fileName + " to Repo");

		try {

			Node folderNode = createNodeForModelId(modelId);
			NodeIterator nodeIt = folderNode.getNodes(FILE_NODES);
			if (!nodeIt.hasNext()) { // new node
				Node fileNode = folderNode.addNode(fileName, "nt:file");
				fileNode.addMixin("vorto:meta");
				fileNode.addMixin("mix:referenceable");
				fileNode.addMixin("mix:lastModified");
				fileNode.setProperty("vorto:author", userContext.getUsername());
				Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
				Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(content));
				contentNode.setProperty("jcr:data", binary);
			} else { // node already exists.
				Node fileNode = nodeIt.nextNode();
				fileNode.addMixin("mix:lastModified");
				fileNode.setProperty("vorto:author", userContext.getUsername());
				Node contentNode = fileNode.getNode("jcr:content");
				Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(content));
				contentNode.setProperty("jcr:data", binary);
			}

			session.save();
			logger.info("Model was saved successful");
			return ModelParserFactory.getParser(fileName).parse(new ByteArrayInputStream(content));
		} catch (Exception e) {
			logger.error("Error checking in model", e);
			throw new FatalModelRepositoryException("Problem checking in uploaded model" + modelId, e);
		}
	}

	@Override
	public ModelInfo getById(ModelId modelId) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);

			Node folderNode = session.getNode(modelIdHelper.getFullPath());

			Node modelFileNode = folderNode.getNodes(FILE_NODES).nextNode();

			ModelInfo modelResource = createModelResource(modelFileNode);

			if (!getAttachmentsByTag(modelId, Attachment.TAG_IMAGE).isEmpty()) {
				modelResource.setHasImage(true);
			}
			
			if (!getAttachmentsByTag(modelId, Attachment.TAG_IMPORTED).isEmpty()) {
				modelResource.setImported(true);
			}

			return modelResource;
		} catch (PathNotFoundException e) {
			return null;
		} catch (RepositoryException e) {
			throw new RuntimeException("Retrieving Content of Resource: Problem accessing repository", e);
		}
	}

	public void setSession(Session session) {
		this.session = session;
	}

	@Override
	public List<ModelInfo> getMappingModelsForTargetPlatform(ModelId modelId, String targetPlatform) {
		List<ModelInfo> mappingResources = new ArrayList<>();
		ModelInfo modelResource = getById(modelId);
		if (modelResource != null) {
			for (ModelId referenceeModelId : modelResource.getReferencedBy()) {
				ModelInfo referenceeModelResources = getById(referenceeModelId);
				if (referenceeModelResources.getType() == ModelType.Mapping
						&& isTargetPlatformMapping(referenceeModelResources, targetPlatform)) {
					mappingResources.add(referenceeModelResources);
				}
			}
			for (ModelId referencedModelId : modelResource.getReferences()) {
				mappingResources.addAll(getMappingModelsForTargetPlatform(referencedModelId, targetPlatform));
			}
		}
		return mappingResources;
	}

	private boolean isTargetPlatformMapping(ModelInfo model, String targetPlatform) {
		try {
			ModelResource emfResource = getEMFResource(model.getId());
			return emfResource.matchesTargetPlatform(targetPlatform);
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	public ModelResource getEMFResource(ModelId modelId) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);

			Node folderNode = session.getNode(modelIdHelper.getFullPath());
			Node fileNode = (Node) folderNode.getNodes().next();
			Node fileItem = (Node) fileNode.getPrimaryItem();
			InputStream is = fileItem.getProperty("jcr:data").getBinary().getStream();
			return (ModelResource) ModelParserFactory.getParser(fileNode.getName()).parse(is);
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	// @Override
	// public void removeModelImage(ModelId modelId) {
	// try {
	// ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
	// Node modelFolderNode = session.getNode(modelIdHelper.getFullPath());
	// if (modelFolderNode.hasNode("img.png")) {
	// Node imageNode = (Node) modelFolderNode.getNode("img.png");
	// imageNode.remove();
	// session.save();
	// }
	// } catch (PathNotFoundException e) {
	// throw new ModelNotFoundException("Problem when trying to remove image to
	// model", e);
	// } catch (RepositoryException e) {
	// throw new FatalModelRepositoryException("Something severe went wrong when
	// accessing the repository", e);
	// }
	// }
	//
	// @Override
	// public byte[] getModelImage(ModelId modelId) {
	// try {
	// ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
	//
	// Node folderNode = session.getNode(modelIdHelper.getFullPath());
	// if (folderNode.hasNode("img.png")) {
	// Node imageNode = folderNode.getNode("img.png");
	// Node fileItem = (Node) imageNode.getPrimaryItem();
	// InputStream is =
	// fileItem.getProperty("jcr:data").getBinary().getStream();
	// return IOUtils.toByteArray(is);
	// }
	// } catch (PathNotFoundException e) {
	// throw new ModelNotFoundException("Problem when trying to retrieve image
	// for model", e);
	// } catch (RepositoryException e) {
	// throw new FatalModelRepositoryException("Something severe went wrong when
	// accessing the repository", e);
	// } catch (IOException e) {
	// throw new FatalModelRepositoryException("Something severe went wrong when
	// trying to read image content", e);
	// }
	// return null;
	// }

	@Override
	public void removeModel(ModelId modelId) {
		try {
			ModelInfo modelResource = this.getById(modelId);
			if (!modelResource.getReferencedBy().isEmpty()) {
				throw new ModelReferentialIntegrityException(
						"Cannot remove model because it is referenced by other model(s)",
						modelResource.getReferencedBy());
			}
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Item item = session.getItem(modelIdHelper.getFullPath());
			item.remove();
			session.save();
		} catch (RepositoryException e) {
			throw new FatalModelRepositoryException("Problem occured removing the model", e);
		}
	}

	@Override
	public ModelInfo updateMeta(ModelInfo model) {
		updateProperty(model.getId(), fileNode -> {
			fileNode.setProperty("vorto:author", model.getAuthor());
			fileNode.setProperty("vorto:state", model.getState());
		});

		return model;
	}

	public ModelId updateState(ModelId modelId, String state) {
		return updateProperty(modelId, node -> node.setProperty("vorto:state", state));
	}

	private ModelId updateProperty(ModelId modelId, NodeConsumer nodeConsumer) {
		try {
			Node folderNode = createNodeForModelId(modelId);
			Node fileNode = folderNode.getNodes(FILE_NODES).hasNext() ? folderNode.getNodes(FILE_NODES).nextNode()
					: null;
			fileNode.addMixin("mix:lastModified");
			nodeConsumer.accept(fileNode);
			session.save();

			return modelId;
		} catch (RepositoryException e) {
			throw new FatalModelRepositoryException("Problem occured removing the model", e);
		}
	}

	@FunctionalInterface
	private interface NodeConsumer {
		void accept(Node node) throws RepositoryException;
	}

	public void saveModel(ModelResource resource) {
		try {
			Node folderNode = createNodeForModelId(resource.getId());
			Node fileNode = folderNode.getNodes(FILE_NODES).hasNext() ? folderNode.getNodes(FILE_NODES).nextNode()
					: null;
			Node contentNode = fileNode.getNode("jcr:content");
			Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(resource.toDSL()));
			contentNode.setProperty("jcr:data", binary);
			session.save();
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Problem occured removing the model", e);
		}
	}

	public ModelSearchUtil getModelSearchUtil() {
		return modelSearchUtil;
	}

	public void setModelSearchUtil(ModelSearchUtil modelSearchUtil) {
		this.modelSearchUtil = modelSearchUtil;
	}

	public IUserRepository getUserRepository() {
		return userRepository;
	}

	public void setUserRepository(IUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public void addFileContent(ModelId modelId, FileContent fileContent) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node folderNode = session.getNode(modelIdHelper.getFullPath());

			Node contentNode = null;

			if (folderNode.hasNode(fileContent.getFileName())) {
				Node imageNode = (Node) folderNode.getNode(fileContent.getFileName());
				contentNode = (Node) imageNode.getPrimaryItem();
			} else {
				Node imageNode = folderNode.addNode(fileContent.getFileName(), "nt:file");
				contentNode = imageNode.addNode("jcr:content", "nt:resource");
			}

			Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(fileContent.getContent()));
			contentNode.setProperty("jcr:data", binary);
			session.save();

		} catch (PathNotFoundException e) {
			throw new ModelNotFoundException("Could not find model with the given model id", e);
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	@Override
	public Optional<FileContent> getFileContent(ModelId modelId, String fileName) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node folderNode = session.getNode(modelIdHelper.getFullPath());

			if (!folderNode.hasNode(fileName)) {
				return Optional.empty();
			}

			Node fileNode = (Node) folderNode.getNode(fileName);
			Node fileItem = (Node) fileNode.getPrimaryItem();
			InputStream is = fileItem.getProperty("jcr:data").getBinary().getStream();

			final String fileContent = IOUtils.toString(is);
			return Optional.of(new FileContent(fileName, fileContent.getBytes()));

		} catch (PathNotFoundException e) {
			throw new ModelNotFoundException("Could not find model with the given model id", e);
		} catch (Exception e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	@Override
	public void attachFile(ModelId modelId, FileContent fileContent, IUserContext userContext, Tag... tags) throws AttachmentException {

		if (Arrays.asList(tags).stream().filter(tag -> tag.equals(Attachment.TAG_IMPORTED)).collect(Collectors.toList()).isEmpty()) {
			attachmentValidator.validateAttachment(fileContent, modelId);
		}
		
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node modelFolderNode = session.getNode(modelIdHelper.getFullPath());

			Node attachmentFolderNode = null;
			if (!modelFolderNode.hasNode("attachments")) {
				attachmentFolderNode = modelFolderNode.addNode("attachments", "nt:folder");
			} else {
				attachmentFolderNode = modelFolderNode.getNode("attachments");
			}
			
			String[] tagIds = Arrays.asList(tags).stream().map(t -> t.getId()).collect(Collectors.toList())
					.toArray(new String[tags.length]);
			
			Node contentNode = null;
			if (attachmentFolderNode.hasNode(fileContent.getFileName())) {
				Node attachmentNode = (Node) attachmentFolderNode.getNode(fileContent.getFileName());
				attachmentNode.addMixin("vorto:meta");
				attachmentNode.setProperty("vorto:tags", tagIds, PropertyType.STRING);
				contentNode = (Node) attachmentNode.getPrimaryItem();
			} else {
				Node attachmentNode = attachmentFolderNode.addNode(fileContent.getFileName(), "nt:file");
				attachmentNode.addMixin("vorto:meta");
				attachmentNode.setProperty("vorto:tags", tagIds, PropertyType.STRING);
				contentNode = attachmentNode.addNode("jcr:content", "nt:resource");
			}
			
			Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(fileContent.getContent()));
			contentNode.setProperty("jcr:data", binary);
			session.save();
		} catch (PathNotFoundException e) {
			throw new ModelNotFoundException("Model with ID "+modelId+" not found");
		} catch (RepositoryException e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}
	
	@Override
	public List<Attachment> getAttachments(ModelId modelId) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node modelFolderNode = session.getNode(modelIdHelper.getFullPath());

			if (modelFolderNode.hasNode("attachments")) {
				Node attachmentFolderNode = modelFolderNode.getNode("attachments");
				List<Attachment> attachments = new ArrayList<Attachment>();
				NodeIterator nodeIt = attachmentFolderNode.getNodes();
				while (nodeIt.hasNext()) {
					Node fileNode = (Node) nodeIt.next();
					Attachment attachment = Attachment.newInstance(modelId, fileNode.getName());
					if (fileNode.hasProperty("vorto:tags")) {
						final List<Value> tags = Arrays.asList(fileNode.getProperty("vorto:tags").getValues());
						attachment.setTags(tags.stream().map(value -> createTag(value)).collect(Collectors.toList()));
					}
					attachments.add(attachment);
				}
				return attachments;
			}

			return Collections.emptyList();
		} catch (PathNotFoundException e) {
			return Collections.emptyList();
		} catch (RepositoryException e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	private Tag createTag(Value tagValue) {
		try {
			if (tagValue.getString().equals(Attachment.TAG_DOCUMENTATION.getId())) {
				return Attachment.TAG_DOCUMENTATION;
			} else if (tagValue.getString().equals(Attachment.TAG_IMAGE.getId())) {
				return Attachment.TAG_IMAGE;
			} else if (tagValue.getString().equals(Attachment.TAG_IMPORTED.getId())) {
				return Attachment.TAG_IMPORTED;
			} else {
				return null;
			}
		} catch (RepositoryException ex) {
			return null;
		}
	}

	@Override
	public List<Attachment> getAttachmentsByTag(final ModelId modelId, final Tag tag) {
		return getAttachments(modelId).stream().filter(attachment -> attachment.getTags().contains(tag))
				.collect(Collectors.toList());
	}

	@Override
	public Optional<FileContent> getAttachmentContent(ModelId modelId, String fileName) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node modelFolderNode = session.getNode(modelIdHelper.getFullPath());

			if (modelFolderNode.hasNode("attachments")) {
				Node attachmentFolderNode = modelFolderNode.getNode("attachments");
				if (attachmentFolderNode.hasNode(fileName)) {
					Node attachment = (Node) attachmentFolderNode.getNode(fileName).getPrimaryItem();
					return Optional.of(new FileContent(fileName,
							IOUtils.toByteArray(attachment.getProperty("jcr:data").getBinary().getStream())));
				}
			}

			return Optional.empty();
		} catch (PathNotFoundException e) {
			return Optional.empty();
		} catch (IOException | RepositoryException e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}

	public boolean deleteAttachment(ModelId modelId, String fileName) {
		try {
			ModelIdHelper modelIdHelper = new ModelIdHelper(modelId);
			Node modelFolderNode = session.getNode(modelIdHelper.getFullPath());

			if (modelFolderNode.hasNode("attachments")) {
				Node attachmentFolderNode = modelFolderNode.getNode("attachments");
				if (attachmentFolderNode.hasNode(fileName)) {
					attachmentFolderNode.getNode(fileName).remove();
					session.save();
					return true;
				}
			}

			return false;
		} catch (PathNotFoundException e) {
			return false;
		} catch (RepositoryException e) {
			throw new FatalModelRepositoryException("Something went wrong accessing the repository", e);
		}
	}
	
	public Session getSession() {
		return this.session;
	}
	
}