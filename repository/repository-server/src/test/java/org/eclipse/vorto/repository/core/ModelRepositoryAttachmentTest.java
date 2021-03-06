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
package org.eclipse.vorto.repository.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.eclipse.vorto.repository.AbstractIntegrationTest;
import org.eclipse.vorto.repository.api.ModelId;
import org.eclipse.vorto.repository.api.ModelInfo;
import org.eclipse.vorto.repository.api.attachment.Attachment;
import org.eclipse.vorto.repository.api.exception.ModelNotFoundException;
import org.eclipse.vorto.repository.core.impl.UserContext;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

public class ModelRepositoryAttachmentTest extends AbstractIntegrationTest {
	
	@Test
	public void testAttachNewFile() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
						
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.size());
			
			assertEquals("backup1.xml",attachments.get(0).getFilename());
			assertEquals(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),attachments.get(0).getModelId());
			assertEquals(0,attachments.get(0).getTags().size());
			attachments.forEach(attachment -> System.out.println(attachment));
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testOverwriteAttachmentWithoutTags() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
				
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup-withImages.xml").getInputStream())), erle);
			
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.size());
			
			assertEquals("backup1.xml",attachments.get(0).getFilename());
			assertEquals(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),attachments.get(0).getModelId());
			assertEquals(0,attachments.get(0).getTags().size());
			attachments.forEach(attachment -> System.out.println(attachment));
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testAttachFileWithTag() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle,Attachment.TAG_DOCUMENTATION);
						
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.get(0).getTags().size());
			assertEquals(Attachment.TAG_DOCUMENTATION, attachments.get(0).getTags().get(0));
			
			attachments.forEach(name -> System.out.println(name));
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testAttachFileWithMultipleTags() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle,Attachment.TAG_DOCUMENTATION,Attachment.TAG_IMAGE);
						
			List<Attachment> attachments = modelRepository.getAttachmentsByTag(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),Attachment.TAG_IMAGE);
			
			assertEquals(2, attachments.get(0).getTags().size());
			
			attachments.forEach(name -> System.out.println(name));
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testGetAttachmentsWithTagExisting() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle,Attachment.TAG_DOCUMENTATION);
						
			List<Attachment> attachments = modelRepository.getAttachmentsByTag(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),Attachment.TAG_DOCUMENTATION);
			
			assertEquals(1,attachments.size());
			assertEquals(Attachment.TAG_DOCUMENTATION, attachments.get(0).getTags().get(0));
						
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testGetAttachmentsWithTagNotExisting() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle,Attachment.TAG_DOCUMENTATION);
						
			List<Attachment> attachments = modelRepository.getAttachmentsByTag(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),Attachment.TAG_IMAGE);
			
			assertEquals(0,attachments.size());
						
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testAttachMultipleFiles() throws Exception {
		IUserContext erle = UserContext.user("erle");
		ModelInfo model = importModel("Color.type", erle);
		attachFile(model);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("Color.xmi", IOUtils.toByteArray(new ClassPathResource("sample_models/Color.xmi").getInputStream())), erle);
			
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(2, attachments.size());
			
			attachments.forEach(name -> System.out.println(name));
			
		} catch (IOException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testExtractAttachedFileContent() throws Exception {
		IUserContext erle = UserContext.user("erle");
		ModelInfo model = importModel("Color.type", erle);
		attachFile(model);
		
		try {
			Optional<FileContent> colorXmi = modelRepository.getAttachmentContent(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), "Color.xmi");
			
			assertFalse(colorXmi.isPresent());
			
			Optional<FileContent> backup1 = modelRepository.getAttachmentContent(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), "backup1.xml");
			
			assertTrue(backup1.isPresent());
			
			byte[] backup1Array = IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream());
			
			assertTrue(Arrays.equals(backup1Array, backup1.get().getContent()));
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test(expected=ModelNotFoundException.class)
	public void testAttachFileToNonExistingModel() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("NotExistModel", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
						
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}	
	
	@Test
	public void testDeleteAttachments() throws Exception {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		ModelId modelId = new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0");
		
		modelRepository.attachFile(modelId, 
				new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
		
		
		boolean deleteResult = modelRepository.deleteAttachment(modelId, "backup1.xml");
		assertTrue(deleteResult);
	}
	
	@Test
	public void testDeleteOfAttachmentTwice() throws Exception {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		ModelId modelId = new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0");
		
		modelRepository.attachFile(modelId, 
				new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
		
		
		modelRepository.deleteAttachment(modelId, "backup1.xml");
		
		boolean deleteResult1 = modelRepository.deleteAttachment(modelId, "backup1.xml");
		assertFalse(deleteResult1);
	}
	
	@Test
	public void testAttachImageWithTag() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("sample.png", IOUtils.toByteArray(new ClassPathResource("sample_models/sample.png").getInputStream())), erle,Attachment.TAG_IMAGE);
						
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.size());
			
			assertEquals("sample.png",attachments.get(0).getFilename());
			assertEquals(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),attachments.get(0).getModelId());
			assertEquals(1,attachments.get(0).getTags().size());
			assertEquals("Image",attachments.get(0).getTags().get(0).getLabel());
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testOverwriteImageWithSameTag() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("sample.png", IOUtils.toByteArray(new ClassPathResource("sample_models/sample.png").getInputStream())), erle,Attachment.TAG_IMAGE);
			
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("sample.png", IOUtils.toByteArray(new ClassPathResource("sample_models/sample.png").getInputStream())), erle,Attachment.TAG_IMAGE);
			
			
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.size());
			
			assertEquals("sample.png",attachments.get(0).getFilename());
			assertEquals(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),attachments.get(0).getModelId());
			assertEquals(1,attachments.get(0).getTags().size());
			assertEquals("Image",attachments.get(0).getTags().get(0).getLabel());
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	@Test
	public void testOverwriteImageWithDiffernentTag() {
		IUserContext erle = UserContext.user("erle");
		importModel("Color.type", erle);
		
		try {
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("sample.png", IOUtils.toByteArray(new ClassPathResource("sample_models/sample.png").getInputStream())), erle,Attachment.TAG_IMAGE);
			
			modelRepository.attachFile(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"), 
					new FileContent("sample.png", IOUtils.toByteArray(new ClassPathResource("sample_models/sample.png").getInputStream())), erle,Attachment.TAG_DOCUMENTATION);
			
			
			List<Attachment> attachments = modelRepository.getAttachments(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"));
			
			assertEquals(1, attachments.size());
			
			assertEquals("sample.png",attachments.get(0).getFilename());
			assertEquals(new ModelId("Color", "org.eclipse.vorto.examples.type", "1.0.0"),attachments.get(0).getModelId());
			assertEquals(1,attachments.get(0).getTags().size());
			assertEquals("Documentation",attachments.get(0).getTags().get(0).getLabel());
			
		} catch (IOException | FatalModelRepositoryException e) {
			e.printStackTrace();
			fail("Cannot load sample file");
		}
	}
	
	private void attachFile(ModelInfo model) throws Exception {
		IUserContext erle = UserContext.user("erle");
		modelRepository.attachFile(model.getId(), 
				new FileContent("backup1.xml", IOUtils.toByteArray(new ClassPathResource("sample_models/backup1.xml").getInputStream())), erle);
	}
}
