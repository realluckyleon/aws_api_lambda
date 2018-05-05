package hello;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateDeploymentResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.DeleteRestApiRequest;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetResourcesResult;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Controller
public class HelloController {

	@RequestMapping("/greeting")
	public String greeting(@RequestParam(value = "name", required = false, defaultValue = "World") String name,
			Model model) throws UnsupportedEncodingException {

		// create zip file
		String filePath = "/Users/Leon/eclipse/workspace/index.zip";
//		String filePath = "/home/ec2-user/index.zip";
		String bucketName = "for-lambda-src-demo";
		String keyName = Paths.get(filePath).getFileName().toString();
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String username = name.replaceAll(" ", "").toLowerCase();
		String str = "'use strict';\n exports.handler = function(event, context) { \n "
				+ "var html = \"<html><head><title>HTML from API Gateway/Lambda</title>\" + \n"
				+ "\"<style> .class{ position: absolute; margin: -40px 0 0 -8px; width: 1200px; height: 620px; "
				+ "background: url(\'https://s3-us-west-2.amazonaws.com/for-lambda-src-demo/aws_certificate_technical_essentials.jpg\') no-repeat;}</style>"
				+ "</head><body><div class=\'class\'><br/><br/><br/><br/><br/>"
				+ "<h1 align=\'center\' style=\'font-family:courier;font-size:350%\'>" + name + "</h1>"
				+ "<br/><br/><br/><br/><br/><br/><br/><br/><br/><br/><br/><br/><br/><br/>"
				+ "<span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
				+ sdf.format(date) + "</span></div></body></html>\"; \n context.succeed(html); };";

		try {
			FileOutputStream fos = new FileOutputStream(filePath);
			ZipOutputStream zos = new ZipOutputStream(fos);
			ZipEntry entry = new ZipEntry("index.js");
			zos.putNextEntry(entry);

			byte[] data = str.getBytes();
			zos.write(data, 0, data.length);

			zos.closeEntry();
			zos.flush();
			zos.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		// put zip file to S3
		File file = new File(filePath);
		if (!file.exists()) {
			System.exit(1);
		}
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		try {
			s3.putObject(bucketName, keyName, file);
		} catch (AmazonServiceException e) {
			System.err.println(e.getErrorMessage());
			System.exit(1);
		}

		AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();

		// create function code
		FunctionCode funcCode = new FunctionCode();
		funcCode.setS3Bucket(bucketName);
		funcCode.setS3Key(keyName);

		// create lambda
		CreateFunctionRequest cfr = new CreateFunctionRequest();
		cfr.setFunctionName(username + "-lambda-" + UUID.randomUUID());
		cfr.setDescription("This lambda app was created for " + name);
		cfr.setRole("arn:aws:iam::424444073756:role/lambda-basic");
		cfr.setHandler("index.handler");
		cfr.setCode(funcCode);
		cfr.setPublish(true);
		cfr.setRuntime("nodejs6.10");
		CreateFunctionResult lambdaResult = lambda.createFunction(cfr);

		AmazonApiGateway apiGateway = AmazonApiGatewayClientBuilder.defaultClient();

		// create rest api
		CreateRestApiRequest crar = new CreateRestApiRequest();
		crar.setName(username + "-api");
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
		Map<String, Boolean> responseParameters = new HashMap<String, Boolean>();
		responseParameters.put("method.response.header.Content-Type", true);
		pmrr.setResponseParameters(responseParameters);
		apiGateway.putMethodResponse(pmrr);

		// put integration
		PutIntegrationRequest pir = new PutIntegrationRequest();
		pir.setHttpMethod("GET");
		pir.setIntegrationHttpMethod("POST");
		pir.setResourceId(resourceId);
		pir.setRestApiId(apiId);
		pir.setType(IntegrationType.AWS);
		pir.setCredentials("arn:aws:iam::424444073756:role/lambda-full");
		// pir.setUri("arn:aws:apigateway:us-west-2:lambda:path/2015-03-31/functions/arn:aws:lambda:us-west-2:424444073756:function:bcd-lambda-applianction-b0a5a87b-8972-4184-a034-f7ef7529329e/invocations");
		pir.setUri("arn:aws:apigateway:us-west-2:lambda:path/2015-03-31/functions/" + lambdaResult.getFunctionArn()
				+ "/invocations");
		apiGateway.putIntegration(pir);

		// put integration response
		PutIntegrationResponseRequest pirr = new PutIntegrationResponseRequest();
		pirr.setResourceId(resourceId);
		pirr.setRestApiId(apiId);
		pirr.setStatusCode("200");
		pirr.setHttpMethod("GET");
		Map<String, String> rp = new HashMap<String, String>();
		rp.put("method.response.header.Content-Type", "'text/html'");
		pirr.setResponseParameters(rp);
		Map<String, String> responseTemplates = new HashMap<String, String>();
		responseTemplates.put("text/html", "$input.json('$')");
		pirr.setResponseTemplates(responseTemplates);
		apiGateway.putIntegrationResponse(pirr);

		// create deployment
		CreateDeploymentRequest cdr = new CreateDeploymentRequest();
		cdr.setDescription("test");
		cdr.setRestApiId(apiId);
		cdr.setStageDescription("test");
		cdr.setStageName("test");
		CreateDeploymentResult cdrslt = apiGateway.createDeployment(cdr);

		file.delete();

		model.addAttribute("name", name);
		model.addAttribute("url",
				"https://" + apiId + ".execute-api.us-west-2.amazonaws.com/" + cdrslt.getDescription());

		return "greeting";
	}

	public static void main(String[] args) {

		AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();
		DeleteFunctionRequest dfr = new DeleteFunctionRequest();
		ListFunctionsResult lfr = lambda.listFunctions();
		ArrayList<FunctionConfiguration> list = (ArrayList<FunctionConfiguration>) lfr.getFunctions();
		for (int i = 0; i < list.size(); i++) {
			dfr.setFunctionName(list.get(i).getFunctionName());
			lambda.deleteFunction(dfr);
		}

		AmazonApiGateway apiGateway = AmazonApiGatewayClientBuilder.defaultClient();
		DeleteRestApiRequest drar = new DeleteRestApiRequest();
		GetRestApisRequest grar = new GetRestApisRequest();
		grar.setLimit(65536);
		GetRestApisResult grarslt = apiGateway.getRestApis(grar);
		ArrayList<RestApi> apiList = (ArrayList<RestApi>) grarslt.getItems();
		for (int i = 0; i < apiList.size(); i++) {
			drar.setRestApiId(apiList.get(i).getId());
			apiGateway.deleteRestApi(drar);
			try {
				Thread.sleep(30000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done!");
	}
}
