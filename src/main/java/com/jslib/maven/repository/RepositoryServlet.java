package com.jslib.maven.repository;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import js.log.Log;
import js.log.LogFactory;
import js.tiny.container.servlet.AppServlet;
import js.tiny.container.servlet.RequestContext;
import js.util.Files;

public class RepositoryServlet extends AppServlet {
	private static final long serialVersionUID = -6430637526088493779L;
	private static final Log log = LogFactory.getLog(RepositoryServlet.class);

	private URI centralRepositoryURI;
	private File repositoryDir;

	public RepositoryServlet() {
		log.trace("RepositoryServlet()");
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		ServletContext servletContext = config.getServletContext();
		log.trace("Initialize servlet |%s#%s|.", servletContext.getServletContextName(), config.getServletName());
		centralRepositoryURI = URI.create(servletContext.getInitParameter("central.repository.uri"));
		repositoryDir = new File(servletContext.getInitParameter("local.repository.dir"));
	}

	@Override
	protected void handleRequest(RequestContext context) throws IOException, ServletException {
		final HttpServletRequest httpRequest = context.getRequest();
		final HttpServletResponse httpResponse = context.getResponse();
		httpResponse.setCharacterEncoding("UTF-8");

		try {
			switch (httpRequest.getMethod()) {
			case "HEAD":
				onHEAD(httpRequest, httpResponse);
				break;

			case "GET":
				onGET(httpRequest, httpResponse);
				break;

			case "POST":
			case "PUT":
				onPOST(httpRequest, httpResponse);
				break;

			case "DELETE":
				onDELETE(httpRequest, httpResponse);
				break;

			default:
				log.error("Method |%s| not implemented for request |%s|.", httpRequest.getMethod(),
						httpRequest.getRequestURI());
				httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				httpResponse.addHeader("Allow", "HEAD,GET,POST,PUT,DELETE");
			}
		} catch (Exception e) {
			log.error(e);
			httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private void onHEAD(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		String requestPath = requestPath(httpRequest);
		log.debug("HEAD request |%s|.", requestPath);
		File file = onFile(requestPath, httpResponse);
		if (file != null) {
			httpResponse.setContentLength((int) file.length());
			httpResponse.setStatus(HttpServletResponse.SC_OK);
		}
	}

	private void onGET(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		String requestPath = requestPath(httpRequest);
		log.debug("GET request |%s|.", requestPath);
		File file = onFile(requestPath, httpResponse);
		if (file != null) {
			httpResponse.setStatus(HttpServletResponse.SC_OK);
			Files.copy(file, httpResponse.getOutputStream());
		}
	}

	private File onFile(String requestPath, HttpServletResponse httpResponse) throws IOException {
		File file = new File(repositoryDir, requestPath);

		if (file.exists() && file.length() == 0) {
			log.debug("Empty file treated as not found |%s|.", file);
			httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}

		if (!file.exists()) {
			File dir = file.getParentFile();
			if (!dir.exists() && !dir.mkdirs()) {
				log.error("Fail to create directory |%s|.", dir);
				httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return null;
			}

			URI fileURI = centralRepositoryURI.resolve(requestPath);
			log.debug("Cache miss. Download |%s|.", fileURI);
			try {
				Files.copy(fileURI.toURL(), file);
			} catch (IOException e) {
				log.error(e);
			}
			if (!file.exists()) {
				log.debug("Cannot load file from central repository |%s|.", fileURI);
				httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return null;
			}
		}

		return file;
	}

	private void onPOST(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		String requestPath = requestPath(httpRequest);
		log.debug("POST request |%s|.", requestPath);

		File file = new File(repositoryDir, requestPath);
		File dir = file.getParentFile();
		if (!dir.exists() && !dir.mkdirs()) {
			throw new IOException("Fail to create directory.");
		}

		Files.copy(httpRequest.getInputStream(), file);
		httpResponse.setStatus(HttpServletResponse.SC_OK);
	}

	private void onDELETE(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		String requestPath = requestPath(httpRequest);
		log.debug("DELETE request |%s|.", requestPath);

		File dir = new File(repositoryDir, requestPath);
		if (!dir.exists()) {
			httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		Files.removeFilesHierarchy(dir);
	}

	private static String requestPath(HttpServletRequest httpRequest) {
		return httpRequest.getRequestURI().substring(httpRequest.getContextPath().length() + 1);
	}
}