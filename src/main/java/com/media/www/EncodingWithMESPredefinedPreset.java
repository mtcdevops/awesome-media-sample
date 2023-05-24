package com.media.www;

import java.io.File;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import javax.naming.AuthenticationException;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.models.Asset;
import com.azure.resourcemanager.mediaservices.models.AssetContainerPermission;
import com.azure.resourcemanager.mediaservices.models.AssetContainerSas;
import com.azure.resourcemanager.mediaservices.models.BuiltInStandardEncoderPreset;
import com.azure.resourcemanager.mediaservices.models.EncoderNamedPreset;
import com.azure.resourcemanager.mediaservices.models.Job;
import com.azure.resourcemanager.mediaservices.models.JobInput;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobInputHttp;
import com.azure.resourcemanager.mediaservices.models.JobOutput;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.ListContainerSasInput;
import com.azure.resourcemanager.mediaservices.models.ListPathsResponse;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpoint;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpointResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingLocator;
import com.azure.resourcemanager.mediaservices.models.StreamingPath;
import com.azure.resourcemanager.mediaservices.models.Transform;
import com.azure.resourcemanager.mediaservices.models.TransformOutput;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

public class EncodingWithMESPredefinedPreset {
	// 미디어 서비스 리소스에 접근하기 위한 계정 정보는 appsettings.json 파일에 저장
	private static final String TRANSFORM_NAME = "AdaptiveBitrate"; // 인코딩 프로파일을 입력
	// private static final String OUTPUT_FOLDER = "Output"; // 인코딩이 완료 후 Output
	// 데이터를 저장 할 디렉토리를 지정(로컬)
	private static final String INPUT_MP4_RESOURCE = "video/hellotest.mp4";
	//private static final String BASE_URI = "https://nurimediastg.blob.core.windows.net/media/"; // 인코딩할 미디어가 있는 URL
	//private static final String MP4_FILE_NAME = "hellotest.mp4"; // 미디어 파일 이름을 지정
	//private static final String INPUT_LABEL = "hellotest"; // 인코딩할 미디어의 라벨을 지정

	// 인코딩 완료 후 동영상을 서비스 할 Streaming Endpoint를 지정
	private static final String STREAMING_ENDPOINT_NAME = "prod-endpoint";

	public static void main(String[] args) {
		// Please make sure you have set configuration in
		// resources/conf/appsettings.json.
		ConfigWrapper config = new ConfigWrapper();
		runEncodingWithMESPredefinedPreset(config);

		config.close();
		System.exit(0); // JVM에게 프로그램이 정상 종료되었음을 알림
	}

	/**
	 * Run the sample.
	 *
	 * @param config This param is of type ConfigWrapper. This class reads values
	 *               from local configuration file.
	 */
	private static void runEncodingWithMESPredefinedPreset(ConfigWrapper config) {
		// Connect to media services, please see
		// https://docs.microsoft.com/en-us/azure/media-services/latest/configure-connect-java-howto
		// for details.
		TokenCredential credential = new ClientSecretCredentialBuilder()
				.clientId(config.getAadClientId())
				.clientSecret(config.getAadSecret())
				.tenantId(config.getAadTenantId())
				.build();
		
		AzureProfile profile = new AzureProfile(config.getAadTenantId(), config.getSubscriptionId(),
				com.azure.core.management.AzureEnvironment.AZURE);

		// MediaServiceManager is the entry point to Azure Media resource management.
		MediaServicesManager manager = MediaServicesManager.configure()
				.withLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
				.authenticate(credential, profile);

		// Creating a unique suffix so that we don't have name collisions if you run the
		// sample
		UUID uuid = UUID.randomUUID();
		String uniqueness = uuid.toString();
		String jobName = "job-" + uniqueness.substring(0, 13);
		String locatorName = "locator-" + uniqueness;
		String outputAssetName = "output-" + uniqueness;
		String inputAssetName = "input-" + uniqueness;
		boolean stopEndpoint = false;

		Scanner scanner = new Scanner(System.in);
		try {
			List<TransformOutput> outputs = new ArrayList<>();
			outputs.add(new TransformOutput().withPreset(
					new BuiltInStandardEncoderPreset().withPresetName(EncoderNamedPreset.ADAPTIVE_STREAMING)));

			// Create the transform.
			System.out.println("Creating a transform...");
			Transform transform = manager.transforms()
					.define(TRANSFORM_NAME)
					.withExistingMediaService(config.getResourceGroup(), config.getAccountName())
					.withOutputs(outputs)
					.create();
			System.out.println("Transform created");

			// Create a new input Asset and upload the specified local video file into it.
			Asset inputAsset = createInputAsset(manager, config.getResourceGroup(), config.getAccountName(),
					inputAssetName, INPUT_MP4_RESOURCE);

			// Create a JobInputHttp. The input to the Job is a HTTPS URL pointing to an MP4
			// file.
//			List<String> files = new ArrayList<>();
//			files.add(MP4_FILE_NAME);
//			JobInputHttp input = new JobInputHttp().withBaseUri(BASE_URI);
//			input.withFiles(files);
//			input.withLabel(INPUT_LABEL);

			// Output from the encoding Job must be written to an Asset, so let's create
			// one. Note that we
			// are using a unique asset name, there should not be a name collision.
			System.out.println("Creating an output asset...");
			Asset outputAsset = manager.assets()
					.define(outputAssetName)
					.withExistingMediaService(config.getResourceGroup(), config.getAccountName())
					.create();

			Job job = submitJob(manager, config.getResourceGroup(), config.getAccountName(), transform.name(), jobName,
					inputAsset.name(), outputAsset.name());

			long startedTime = System.currentTimeMillis();

			// In this demo code, we will poll for Job status. Polling is not a recommended
			// best practice for production
			// applications because of the latency it introduces. Overuse of this API may
			// trigger throttling. Developers
			// should instead use Event Grid. To see how to implement the event grid, see
			// the sample
			// https://github.com/Azure-Samples/media-services-v3-java/tree/master/ContentProtection/BasicAESClearKey.
			job = waitForJobToFinish(manager, config.getResourceGroup(), config.getAccountName(), transform.name(),
					jobName);

			long elapsed = (System.currentTimeMillis() - startedTime) / 1000; // Elapsed time in seconds
			System.out.println("Job elapsed time: " + elapsed + " second(s).");

			if (job.state() == JobState.FINISHED) {
				System.out.println("Job finished.");
				System.out.println();

				// Now that the content has been encoded, publish it for Streaming by creating
				// a StreamingLocator.
				StreamingLocator locator = getStreamingLocator(manager, config.getResourceGroup(),
						config.getAccountName(), outputAsset.name(), locatorName);

				StreamingEndpoint streamingEndpoint = manager.streamingEndpoints().get(config.getResourceGroup(),
						config.getAccountName(), STREAMING_ENDPOINT_NAME);

				if (streamingEndpoint != null) {
					// Start The Streaming Endpoint if it is not running.
					if (streamingEndpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
						System.out.println("Streaming endpoint was stopped, restarting it...");
						manager.streamingEndpoints().start(config.getResourceGroup(), config.getAccountName(),
								STREAMING_ENDPOINT_NAME);

						// We started the endpoint, we should stop it in cleanup.
						stopEndpoint = true;
					}

					System.out.println();
					System.out.println("Streaming urls:");
					List<String> urls = getStreamingUrls(manager, config.getResourceGroup(), config.getAccountName(),
							locator.name(), streamingEndpoint);

					for (String url : urls) {
						System.out.println("\t" + url);
					}

					System.out.println();
					System.out.println(
							"To stream, copy and paste the Streaming URL into the Azure Media Player at 'http://aka.ms/azuremediaplayer'.");
					System.out.println("When finished, press ENTER to continue.");
					System.out.println();
					System.out.flush();
					scanner.nextLine();

					// Download output asset for verification.
//					System.out.println("Downloading output asset...");
//					System.out.println();
//					File outputFolder = new File(OUTPUT_FOLDER);
//					if (outputFolder.exists() && !outputFolder.isDirectory()) {
//						outputFolder = new File(OUTPUT_FOLDER + uniqueness);
//					}
//					if (!outputFolder.exists()) {
//						outputFolder.mkdir();
//					}
//
//					downloadResults(manager, config.getResourceGroup(), config.getAccountName(), outputAsset.name(),
//							outputFolder);
//
//					System.out.println("Done downloading. Please check the files at " + outputFolder.getAbsolutePath());
				} else {
					System.out.println("Could not find streaming endpoint: " + STREAMING_ENDPOINT_NAME);
				}

				System.out.println("When finished, press ENTER to cleanup.");
				System.out.println();
				System.out.flush();
				scanner.nextLine();
			} else if (job.state() == JobState.ERROR) {
				System.out.println("ERROR: Job finished with error message: " + job.outputs().get(0).error().message());
				System.out.println("ERROR:                   error details: "
						+ job.outputs().get(0).error().details().get(0).message());
			}
		} catch (Exception e) {
			Throwable cause = e;
			while (cause != null) {
				if (cause instanceof AuthenticationException) {
					System.out.println(
							"ERROR: Authentication error, please check your account settings in appsettings.json.");
					break;
				} else if (cause instanceof ManagementException) {
					ManagementException apiException = (ManagementException) cause;
					System.out.println("ERROR: " + apiException.getValue().getMessage());
					break;
				}
				cause = cause.getCause();
			}
			System.out.println();
			e.printStackTrace();
			System.out.println();
		} finally {
			System.out.println("Cleaning up...");
			if (scanner != null) {
				scanner.close();
			}
			cleanup(manager, config.getResourceGroup(), config.getAccountName(), TRANSFORM_NAME, jobName,
					inputAssetName, outputAssetName, locatorName, stopEndpoint, STREAMING_ENDPOINT_NAME);
			System.out.println("Done.");
		}
	}

	/**
	 * Creates a new input Asset and uploads the specified local video file into it.
	 *
	 * @param manager           This is the entry point of Azure Media resource
	 *                          management.
	 * @param resourceGroupName The name of the resource group within the Azure
	 *                          subscription.
	 * @param accountName       The Media Services account name.
	 * @param assetName         The name of the asset where the media file to
	 *                          uploaded to.
	 * @param mediaFile         The path of a media file to be uploaded into the
	 *                          asset.
	 * @return The asset.
	 */

	private static Asset createInputAsset(MediaServicesManager manager, String resourceGroupName, String accountName,
			String assetName, String mediaFile) throws Exception {

		System.out.println("Creating an input asset...");
		// Call Media Services API to create an Asset.
		// This method creates a container in storage for the Asset.
		// The files (blobs) associated with the asset will be stored in this container.
		Asset asset = manager.assets().define(assetName).withExistingMediaService(resourceGroupName, accountName)
				.create();
		ListContainerSasInput parameters = new ListContainerSasInput()
				.withPermissions(AssetContainerPermission.READ_WRITE).withExpiryTime(OffsetDateTime.now().plusHours(4));
		AssetContainerSas response = manager.assets().listContainerSas(resourceGroupName, accountName, assetName,
				parameters);

		// Use Storage API to get a reference to the Asset container
		// that was created by calling Asset's create method.
		BlobContainerClient container = new BlobContainerClientBuilder()
				.endpoint(response.assetContainerSasUrls().get(0)).buildClient();

		// Uploading from a local file:
		// The file is a resource in CLASSPATH.
		URI fileToUpload = EncodingWithMESPredefinedPreset.class.getClassLoader().getResource(mediaFile).toURI(); 

		File file = new File(fileToUpload);
		BlobClient blob = container.getBlobClient(file.getName());

		// Use Storage API to upload the file into the container in storage.
		System.out.println("Uploading a media file to the asset...");
		blob.uploadFromFile(file.getPath());

		return asset;
	}

	/**
	 * Create and submit a job.
	 *
	 * @param manager         The entry point of Azure Media resource management.
	 * @param resourceGroup   The name of the resource group within the Azure
	 *                        subscription.
	 * @param accountName     The Media Services account name.
	 * @param transformName   The name of the transform.
	 * @param jobName         The name of the job.
	 * @param jobInput        The input to the job.
	 * @param outputAssetName The name of the asset that the job writes to.
	 * @return The job created.
	 */
	private static Job submitJob(MediaServicesManager manager, String resourceGroup, String accountName,
			String transformName, String jobName, String inputAssetName, String outputAssetName) {
		System.out.println("Creating a job...");
		// First specify where the output(s) of the Job need to be written to
		JobInput jobInput = new JobInputAsset().withAssetName(inputAssetName);
		
		List<JobOutput> jobOutputs = new ArrayList<>();
		jobOutputs.add(new JobOutputAsset().withAssetName(outputAssetName));
		
		Job job;
        try {
            System.out.println("Creating a job...");
            job = manager.jobs().define(jobName)
                    .withExistingTransform(resourceGroup, accountName, transformName)
                    .withInput(jobInput)
                    .withOutputs(jobOutputs)
                    .create();
        } catch (ManagementException exception) {
            System.out.println("ERROR: API call failed with error code " + exception.getValue().getCode() +
                    " and message '" + exception.getValue().getMessage() + "'");
            throw exception;
        }

//		Job job = manager.jobs().define(jobName).withExistingTransform(resourceGroup, accountName, transformName)
//				.withInput(jobInput).withOutputs(jobOutputs).create();

		return job;
	}

	/**
	 * Polls Media Services for the status of the Job.
	 *
	 * @param manager       This is the entry point of Azure Media resource
	 *                      management
	 * @param resourceGroup The name of the resource group within the Azure
	 *                      subscription
	 * @param accountName   The Media Services account name
	 * @param transformName The name of the transform
	 * @param jobName       The name of the job submitted
	 * @return The job
	 */
	private static Job waitForJobToFinish(MediaServicesManager manager, String resourceGroup, String accountName,
			String transformName, String jobName) {
		final int SLEEP_INTERVAL = 10 * 1000;

		Job job = null;
		boolean exit = false;

		do {
			job = manager.jobs().get(resourceGroup, accountName, transformName, jobName);

			if (job.state() == JobState.FINISHED || job.state() == JobState.ERROR || job.state() == JobState.CANCELED) {
				exit = true;
			} else {
				System.out.println("Job is " + job.state());

				int i = 0;
				for (JobOutput output : job.outputs()) {
					System.out.print("\tJobOutput[" + i++ + "] is " + output.state() + ".");
					if (output.state() == JobState.PROCESSING) {
						System.out.print("  Progress: " + output.progress());
					}
					System.out.println();
				}

				try {
					Thread.sleep(SLEEP_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while (!exit);

		return job;
	}

	/**
	 * Use Media Service and Storage APIs to download the output files to a local
	 * folder
	 *
	 * @param manager       The entry point of Azure Media resource management
	 * @param resourceGroup The name of the resource group within the Azure
	 *                      subscription
	 * @param accountName   The Media Services account name
	 * @param assetName     The asset name
	 * @param outputFolder  The output folder for downloaded files.
	 * @throws Exception
	 * @throws URISyntaxException
	 * @throws IOException
	 */
//	private static void downloadResults(MediaServicesManager manager, String resourceGroup, String accountName,
//			String assetName, File outputFolder) throws URISyntaxException, IOException {
//		ListContainerSasInput parameters = new ListContainerSasInput().withPermissions(AssetContainerPermission.READ)
//				.withExpiryTime(OffsetDateTime.now().plusHours(1));
//		AssetContainerSas assetContainerSas = manager.assets().listContainerSas(resourceGroup, accountName, assetName,
//				parameters);
//
//		BlobContainerClient container = new BlobContainerClientBuilder()
//				.endpoint(assetContainerSas.assetContainerSasUrls().get(0)).buildClient();
//
//		File directory = new File(outputFolder, assetName);
//		directory.mkdir();
//
//		container.listBlobs().forEach(blobItem -> {
//			BlobClient blob = container.getBlobClient(blobItem.getName());
//			File downloadTo = new File(directory, blobItem.getName());
//			blob.downloadToFile(downloadTo.getAbsolutePath());
//		});
//
//		System.out.println("Download complete.");
//	}

	/**
	 * Creates a StreamingLocator for the specified asset and with the specified
	 * streaming policy name. Once the StreamingLocator is created the output asset
	 * is available to clients for playback.
	 *
	 * @param manager       The entry point of Azure Media resource management
	 * @param resourceGroup The name of the resource group within the Azure
	 *                      subscription
	 * @param accountName   The Media Services account name
	 * @param assetName     The name of the output asset
	 * @param locatorName   The StreamingLocator name (unique in this case)
	 * @return The locator created
	 */
	private static StreamingLocator getStreamingLocator(MediaServicesManager manager, String resourceGroup,
			String accountName, String assetName, String locatorName) {
		// Note that we are using one of the PredefinedStreamingPolicies which tell the
		// Origin component
		// of Azure Media Services how to publish the content for streaming.
		System.out.println("Creating a streaming locator...");
		StreamingLocator locator = manager.streamingLocators().define(locatorName)
				.withExistingMediaService(resourceGroup, accountName).withAssetName(assetName)
				.withStreamingPolicyName("Predefined_ClearStreamingOnly").create();

		return locator;
	}

	/**
	 * Checks if the streaming endpoint is in the running state, if not, starts it.
	 *
	 * @param manager           The entry point of Azure Media resource management
	 * @param resourceGroup     The name of the resource group within the Azure
	 *                          subscription
	 * @param accountName       The Media Services account name
	 * @param locatorName       The name of the StreamingLocator that was created
	 * @param streamingEndpoint The streaming endpoint.
	 * @return List of streaming urls
	 */
	private static List<String> getStreamingUrls(MediaServicesManager manager, String resourceGroup, String accountName,
			String locatorName, StreamingEndpoint streamingEndpoint) {
		List<String> streamingUrls = new ArrayList<>();

		ListPathsResponse paths = manager.streamingLocators().listPaths(resourceGroup, accountName, locatorName);

		for (StreamingPath path : paths.streamingPaths()) {
			StringBuilder uriBuilder = new StringBuilder();
			uriBuilder.append("https://").append(streamingEndpoint.hostname()).append("/").append(path.paths().get(0));

			streamingUrls.add(uriBuilder.toString());
		}
		return streamingUrls;
	}

	/**
	 * Cleanup
	 *
	 * @param manager               The entry point of Azure Media resource
	 *                              management.
	 * @param resourceGroupName     The name of the resource group within the Azure
	 *                              subscription.
	 * @param accountName           The Media Services account name.
	 * @param transformName         The transform name.
	 * @param jobName               The job name.
	 * @param assetName             The asset name.
	 * @param streamingLocatorName  The streaming locator name.
	 * @param stopEndpoint          Stop endpoint if true, otherwise keep endpoint
	 *                              running.
	 * @param streamingEndpointName The endpoint name.
	 */
	private static void cleanup(MediaServicesManager manager, String resourceGroupName, String accountName,
			String transformName, String jobName, String inputAssetName, String outputAssetName, String streamingLocatorName, boolean stopEndpoint,
			String streamingEndpointName) {
		if (manager == null) {
			return;
		}

		manager.jobs().delete(resourceGroupName, accountName, transformName, jobName);
		manager.assets().delete(resourceGroupName, accountName, inputAssetName);
        manager.assets().delete(resourceGroupName, accountName, outputAssetName);
		manager.streamingLocators().delete(resourceGroupName, accountName, streamingLocatorName);

		if (stopEndpoint) {
			// Because we started the endpoint, we'll stop it.
			manager.streamingEndpoints().stop(resourceGroupName, accountName, streamingEndpointName);
		} else {
			// We will keep the endpoint running because it was not started by this sample.
			// Please note, There are costs to keep it running.
			// Please refer
			// https://azure.microsoft.com/en-us/pricing/details/media-services/ for
			// pricing.
			System.out.println("The endpoint '" + streamingEndpointName
					+ "' is running. To halt further billing on the endpoint, please stop it in azure portal or AMS Explorer.");
		}
	}
}
