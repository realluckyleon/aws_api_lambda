package hello;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateDeploymentResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetResourcesResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.Resource;

@Controller
public class AWSController {

	@RequestMapping("/greeting1")
	public String greeting(@RequestParam(value = "name", required = false, defaultValue = "World") String name,
			Model model) throws UnsupportedEncodingException {
		
		AmazonApiGateway apiGateway = AmazonApiGatewayClientBuilder.defaultClient();
		
		// create rest api
		name = name.replaceAll(" ", "").toLowerCase();
		CreateRestApiRequest crar = new CreateRestApiRequest();
		crar.setName(name + "-api");
		crar.setDescription("This apigateway was created for " + name);
		CreateRestApiResult apiResult = apiGateway.createRestApi(crar);
		String apiId = apiResult.getId();

		// get resources
		GetResourcesRequest grr = new GetResourcesRequest();
		Collection<String> embed = new ArrayList<String>();
		grr.setEmbed(embed);
		grr.setRestApiId(apiId);
		GetResourcesResult resourceResult = apiGateway.getResources(grr);

		// get resource id
		ArrayList<Resource> list = (ArrayList<Resource>) resourceResult.getItems();
		Resource resource = list.get(0);
		String resourceId = resource.getId();
		
		// put method
		PutMethodRequest pmr = new PutMethodRequest();
		pmr.setHttpMethod("GET");
		pmr.setResourceId(resourceId);
		pmr.setRestApiId(apiId);
		pmr.setAuthorizationType("NONE");
		apiGateway.putMethod(pmr);

		// put method response
		PutMethodResponseRequest pmrr = new PutMethodResponseRequest();
		pmrr.setHttpMethod("GET");
		pmrr.setResourceId(resourceId);
		pmrr.setRestApiId(apiId);
		pmrr.setStatusCode("200");
		apiGateway.putMethodResponse(pmrr);

		// put integration
		PutIntegrationRequest pir = new PutIntegrationRequest();
		pir.setHttpMethod("GET");
		pir.setIntegrationHttpMethod("POST");
		pir.setResourceId(resourceId);
		pir.setRestApiId(apiId);
		pir.setType(IntegrationType.AWS);
		pir.setCredentials("arn:aws:iam::424444073756:role/lambda-full");
		pir.setUri("arn:aws:apigateway:us-west-2:lambda:path/2015-03-31/functions/arn:aws:lambda:us-west-2:424444073756:function:Hello/invocations");
		apiGateway.putIntegration(pir);

		// put integration response
		PutIntegrationResponseRequest pirr = new PutIntegrationResponseRequest();
		pirr.setResourceId(resourceId);
		pirr.setRestApiId(apiId);
		pirr.setStatusCode("200");
		pirr.setHttpMethod("GET");
		apiGateway.putIntegrationResponse(pirr);

		// create deployment
		CreateDeploymentRequest cdr = new CreateDeploymentRequest();
		cdr.setDescription("test");
		cdr.setRestApiId(apiId);
		cdr.setStageDescription("test");
		cdr.setStageName("test");
		CreateDeploymentResult cdrslt = apiGateway.createDeployment(cdr);

		model.addAttribute("name", name);
		model.addAttribute("url","https://" + apiId + ".execute-api.us-west-2.amazonaws.com/" + cdrslt.getDescription());

		return "greeting";
	}

}
