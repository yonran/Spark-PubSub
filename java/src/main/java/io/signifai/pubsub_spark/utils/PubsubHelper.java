/**
 * Copyright 2016 SignifAI, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.signifai.pubsub_spark.utils;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.api.services.pubsub.model.Subscription;
import com.google.api.services.pubsub.model.Topic;
import com.google.common.base.Preconditions;

public class PubsubHelper {
	private static final Logger logger = LoggerFactory.getLogger(PubsubHelper.class);

	private static final String ALREADY_EXISTS = "ALREADY_EXISTS";

	public static HttpTransport getPubSubHttpTransport() {
		return Utils.getDefaultTransport();
	}

	public static JsonFactory getPubSubJsonFactory() {
		return Utils.getDefaultJsonFactory();

	}

	public static Pubsub createPubsubClient() throws IOException {
		final HttpTransport pubSubHttpTransport = getPubSubHttpTransport();
		final JsonFactory pubSubJsonFactory = getPubSubJsonFactory();

		return createPubsubClient(pubSubHttpTransport, pubSubJsonFactory);
	}

	public static Pubsub createPubsubClient(HttpTransport httpTransport, JsonFactory jsonFactory) throws IOException {
		Preconditions.checkNotNull(httpTransport);
		Preconditions.checkNotNull(jsonFactory);

		GoogleCredential credential = GoogleCredential.getApplicationDefault(httpTransport, jsonFactory);
		// In some cases, you need to add the scope explicitly.
		if (credential.createScopedRequired()) {
			credential = credential.createScoped(PubsubScopes.all());
		}
		// Please use custom HttpRequestInitializer for automatic
		// retry upon failures. We provide a simple reference
		// implementation in the "Retry Handling" section.
		HttpRequestInitializer initializer = new RetryHttpInitializerWrapper(credential);
		return new Pubsub.Builder(httpTransport, jsonFactory, initializer).build();
	}

	public static void createTopic(Pubsub pubsub, String topicName) throws IOException {
		if (pubsub == null) {
			logger.info("Not creating topic {} since pubsub has not been configured", topicName);
			return;
		}

		try {
			Topic newTopic = pubsub.projects().topics().create(topicName, new Topic()).execute();
			logger.info("Created: " + newTopic.getName());
		} catch (GoogleJsonResponseException e) {
			final GoogleJsonError details = e.getDetails();
			boolean ignorable = true;

			final Object objectStatus = details.get("status");
			if (objectStatus instanceof String) {
				String status = (String) objectStatus;
				if (!ALREADY_EXISTS.equals(status)) {
					ignorable = false;
				} else {
					logger.info("Topic {} already exists.", topicName);
				}
			}

			if (!ignorable) {
				throw e;
			}
		}
	}

	public static void subscribe(Pubsub pubsub, String topicName, String subscriptionName, Integer ackDeadlineSec)
			throws IOException {
		if (pubsub == null) {
			logger.info("Not creating subscription {} for topic {} since pubsub has not been configured", subscriptionName,
					topicName);
			return;
		}

		Subscription subscription = new Subscription()
				// The name of the topic from which this subscription
				// receives messages
				.setTopic(topicName);

		if (ackDeadlineSec != null) {
			// Ackowledgement deadline in second
			subscription.setAckDeadlineSeconds(ackDeadlineSec);
		}

		try {
			Subscription newSubscription = pubsub.projects().subscriptions().create(subscriptionName, subscription)
					.execute();
			logger.info("Created: " + newSubscription.getName());
		} catch (GoogleJsonResponseException e) {
			final GoogleJsonError details = e.getDetails();
			boolean ignorable = true;

			final Object objectStatus = details.get("status");
			if (objectStatus instanceof String) {
				String status = (String) objectStatus;
				if (!ALREADY_EXISTS.equals(status)) {
					ignorable = false;
				} else {
					logger.info("Subscription {} already exists.", subscriptionName);
				}
			}

			if (!ignorable) {
				throw e;
			}
		}
	}
}
