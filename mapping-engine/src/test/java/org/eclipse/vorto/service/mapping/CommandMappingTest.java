package org.eclipse.vorto.service.mapping;

import java.util.HashMap;

import org.eclipse.vorto.service.mapping.json.JsonData;
import org.eclipse.vorto.service.mapping.normalized.Command;
import org.eclipse.vorto.service.mapping.spec.IMappingSpecification;
import org.eclipse.vorto.service.mapping.spec.MappingSpecificationBuilder;
import org.eclipse.vorto.service.mapping.spec.SpecWithOperationMapping;
import org.eclipse.vorto.service.mapping.spec.SpecWithOperationRule;
import org.junit.Ignore;
import org.junit.Test;

public class CommandMappingTest {

	@Test
	public void testMapSimpleOperation() throws Exception {
	
		IMappingSpecification mappingSpecification = new SpecWithOperationRule();
		IDataMapper<JsonData> mapper = IDataMapper.newBuilder().withSpecification(mappingSpecification).buildCommandMapper();

		Command cmd = Command.forFunctionBlockProperty("button").name("press").build();
		
		JsonData mappedOutput = mapper.map(DataInputFactory.getInstance().fromObject(cmd), MappingContext.empty());
		System.out.println(mappedOutput.toJson());
	}
	
	@Ignore
	public void testMapOperationWithParams() throws Exception {
		IMappingSpecification mappingSpecification = MappingSpecificationBuilder.create()
//				.remoteClient(repository)
				.infomodelId("com.bshg.CoffeeMakerCTL636ES1:1.0.0")
				.targetPlatformKey("homeconnect").build();
		IDataMapper<JsonData> mapper = IDataMapper.newBuilder().withSpecification(mappingSpecification).buildCommandMapper();

		Command cmd = Command.forFunctionBlockProperty("coffeeMaker").name("makeCoffee").param("program","Espresso").param("beanAmount","Mild").build();
		
		JsonData mappedOutput = mapper.map(DataInputFactory.getInstance().fromObject(cmd), MappingContext.empty());
		System.out.println(mappedOutput.toJson());
	}
	
	@Test
	public void testMapOperationWithComplexParam() throws Exception {
		IMappingSpecification mappingSpecification = new SpecWithOperationMapping();
		IDataMapper<JsonData> mapper = IDataMapper.newBuilder().withSpecification(mappingSpecification).buildCommandMapper();

		HashMap<String, Object> obj = new HashMap<>();
		obj.put("count", 4);
		Command cmd = Command.forFunctionBlockProperty("button").name("press").param("obj",obj).build();
		
		JsonData mappedOutput = mapper.map(DataInputFactory.getInstance().fromObject(cmd), MappingContext.empty());
		System.out.println(mappedOutput.toJson());
	}
}
