package com.kount.ris;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.kount.ris.transport.HttpApiTransport;
import com.kount.ris.transport.KountHttpTransport;
import com.kount.ris.transport.Transport;
import com.kount.ris.util.RisException;
import com.kount.ris.util.RisResponseException;
import com.kount.ris.util.RisTransportException;
import com.kount.ris.util.RisValidationException;
import com.kount.ris.util.ValidationError;

/**
 * Controller class for the Kount RIS SDK.
 * </p>
 * This class is responsible for the correct communication between the RIS client and the RIS server.
 * It performs parameter validation, request transport, and response parsing.
 *
 * @author Kount &lt;custserv@kount.com&gt;
 * @version $Id$
 * @copyright 2010 Keynetics Inc
 */
public class KountRisClient {

	/**
	 * Logger.
	 */
	private static final Log logger = LogFactory.getLog(KountRisClient.class);
	
	/**
	 * Transport to use for sending RIS request.
	 */
	protected Transport transport;

	/**
	 * Client side request validator.
	 */
	protected RisValidator validator = new RisValidator();

	/**
	 * StringBuilder to accumulate any error message found in the response being processed.
	 */
	protected StringBuilder errorMessage = null;

	/**
	 * API key for merchant-server authentication.
	 */
	protected String apiKey;

	/**
	 * Explicit default constructor.
	 */
	public KountRisClient() {
	}

	/**
	 * Constructor that accepts a pass phrase.
	 *
	 * @param phrase
	 *            Private key pass phrase
	 * @param url
	 *            RIS server url
	 * @param p12file
	 *            Path to PKCS12 private key file
	 */
	public KountRisClient(String phrase, String url, String p12file) {
		logger.debug("RIS endpoint URL [" + url + "]");
		transport = new KountHttpTransport(phrase, url, p12file);
	}

	/**
	 * Constructor that accepts a pass phrase.
	 *
	 * @param phrase
	 *            Private key pass phrase
	 * @param url
	 *            RIS server url
	 * @param p12in
	 *            PKCS12 private key file input stream
	 */
	public KountRisClient(String phrase, String url, InputStream p12in) {
		transport = new KountHttpTransport(phrase, url, p12in);
	}

	/**
	 * Constructor for using API Keys instead of Certificates.
	 *
	 * @param url
	 *            Ris server URL
	 * @param apiKeyFile
	 *            API key file (absolute path)
	 * @throws RisTransportException
	 *             Exception if opening the api key file has a problem.
	 */
	public KountRisClient(URL url, File apiKeyFile) throws RisTransportException {
		getApiKey(apiKeyFile);
		transport = new HttpApiTransport(url, apiKey);
	}

	/**
	 * Constructor for using API Key instead of Cert.
	 *
	 * @param url
	 *            Ris server URL.
	 * @param key
	 *            API key (key data as a string).
	 */
	public KountRisClient(URL url, String key) {
		setApiKey(key);
		transport = new HttpApiTransport(url, apiKey);
	}

	/**
	 * Set the transport object to use. If not specified the default transport
	 * object used is KountHttpTransport.
	 *
	 * @param t
	 *            Transport
	 */
	public void setTransport(Transport t) {
		transport = t;
	}

	/**
	 * Set api key to use.
	 * 
	 * @param key
	 *            Api key (value).
	 */
	public void setApiKey(String key) {
		apiKey = key;
	}

	/**
	 * Get the last combined message of client side validation errors encountered.
	 *
	 * @return Message
	 */
	public String getErrorMessage() {
		return errorMessage.toString();
	}

	/**
	 * Performs the actions of validating, sending, and parsing a RIS request.
	 *
	 * @throws RisException
	 *             A subclass of RisException will be thrown which will be of
	 *             the type RisResponseException, RisTransportException, or
	 *             RisValidationException.
	 * @param r
	 *            Request
	 * @return Response
	 */
	public Response process(Request r) throws RisException {
		List<ValidationError> errors = this.validate(r);
		errorMessage = new StringBuilder();
		
		if (errors.size() > 0) {
			for (ValidationError error : errors) {
				errorMessage.append(error.toString()).append('\n');;
			}
			throw new RisValidationException(this.errorMessage.toString(), errors);
		}

		Reader reader = send(r);
		Response responseObj = parse(reader);
		if (r.closeOnFinish) {
			try {
				reader.close();
			} catch (IOException e) {
				throw new RisTransportException("Error closing reader", e);
			}
		}
		return responseObj;
	}

	/**
	 * Client side validate the data contained in a RIS request.
	 *
	 * @throws RisValidationException
	 *             RIS validation exception
	 * @param r
	 *            Request object containing data to send to RIS
	 * @return List of errors encountered as com.kount.ris.util.ValidationError
	 *         objects
	 */
	protected List<ValidationError> validate(Request r) throws RisValidationException {
		logger.trace("validate()");
		return validator.validate(r.getParams());
	}

	/**
	 * Send a RIS request.
	 *
	 * @throws RisTransportException
	 *             RIS transport exception
	 * @param r
	 *            Request object to send.
	 * @return Reader for character streams returned by RIS
	 */
	protected Reader send(Request r) throws RisTransportException {
		logger.trace("send()");
		if (transport != null) {
			return transport.send(r.getParams());
		} else {
			throw new RisTransportException("No transport was specified, unable to send request.");
		}
	}

	/**
	 * Parse a collection of key-value strings into a Response object.
	 *
	 * @throws RisResponseException
	 *             RIS response exception
	 * @param r
	 *            Reader for character stream returned by RIS
	 * @return Response object
	 */
	protected Response parse(Reader r) throws RisResponseException {
		logger.trace("parse()");
		return Response.parseResponse(r);
	}

	/**
	 * Fetch data contained in api key file.
	 *
	 * @param apiKeyFile
	 *            API key file.
	 * @return String API Key.
	 * @throws RisTransportException
	 *             RIS transport exception
	 */
	protected final String getApiKey(File apiKeyFile) throws RisTransportException {
		logger.trace("getApiKey()");
		if (apiKey == null && apiKeyFile != null) {
			try {
				byte[] keyBytes = Files.readAllBytes(Paths.get(apiKeyFile.toURI()));
				String key = new String(keyBytes, Charset.forName("UTF-8"));
				setApiKey(key.trim());
			} catch (IOException e) {
				logger.error("API Key file (" + apiKeyFile + ") could not be found:\n" + e);
				throw new RisTransportException("API Key file (" + apiKeyFile + ") could not be found:\n" + e);
			}
		}
		return apiKey;
	}
}