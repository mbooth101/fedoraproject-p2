/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.xmvn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fedoraproject.xmvn.artifact.Artifact;
import org.fedoraproject.xmvn.artifact.DefaultArtifact;
import org.fedoraproject.xmvn.config.PackagingRule;
import org.fedoraproject.xmvn.metadata.ArtifactMetadata;
import org.fedoraproject.xmvn.tools.install.ArtifactInstallationException;
import org.fedoraproject.xmvn.tools.install.ArtifactInstaller;
import org.fedoraproject.xmvn.tools.install.Directory;
import org.fedoraproject.xmvn.tools.install.File;
import org.fedoraproject.xmvn.tools.install.JavaPackage;
import org.fedoraproject.xmvn.tools.install.RegularFile;
import org.fedoraproject.xmvn.tools.install.SymbolicLink;

import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.Provide;
import org.fedoraproject.p2.osgi.OSGiServiceLocator;

@Named("eclipse")
@Singleton
public class EclipseArtifactInstaller implements ArtifactInstaller {
	private final Logger logger = LoggerFactory
			.getLogger(EclipseArtifactInstaller.class);

	@Inject
	private OSGiServiceLocator equinox;

	private final EclipseInstallationRequest request = new EclipseInstallationRequest();

	private final Map<String, JavaPackage> packageMap = new LinkedHashMap<>();

	private final Map<String, ArtifactMetadata> featureMetadataMap = new LinkedHashMap<>();

	private final Map<String, ArtifactMetadata> pluginMetadataMap = new LinkedHashMap<>();

	@Override
	public void install(JavaPackage targetPackage, ArtifactMetadata am,
			PackagingRule rule, String basePackageName)
			throws ArtifactInstallationException {
		Path path = Paths.get(am.getPath());

		if (!am.getExtension().equals("jar") || (!am.getClassifier().isEmpty() && !am.getClassifier().equals("sources")))
			return;

		String type = am.getProperties().getProperty("type");
		boolean isFeature = type.equals("eclipse-feature");
		if (type.equals("eclipse-plugin") || type.equals("eclipse-test-plugin"))
			request.addPlugin(path);
		else if (isFeature)
			request.addFeature(path);
		else
			return;

		Artifact artifact = new DefaultArtifact(am.getGroupId(),
				am.getArtifactId(), am.getExtension(), am.getClassifier(),
				am.getVersion());
		logger.info("Installing artifact {}", artifact);

		String commonId = basePackageName.replaceAll("^eclipse-", "");
		request.setMainPackageId(commonId);
		String subpackageId = targetPackage.getId().replaceAll("^eclipse-", "");
		if (subpackageId.isEmpty())
			subpackageId = commonId;
		else if (!subpackageId.startsWith(commonId + "-"))
			subpackageId = commonId + "-" + subpackageId;

		if (isFeature
				|| (rule.getTargetPackage() != null && !rule.getTargetPackage()
						.isEmpty())) {
			String unitId = null;
			if (isFeature) {
				unitId = am.getArtifactId() + ".feature.group";
			} else if ("sources".equals(am.getClassifier())) {
				unitId = am.getArtifactId() + ".source";
			} else {
				unitId = am.getArtifactId();
			}
			request.addPackageMapping(unitId, subpackageId);
		}

		packageMap.put(subpackageId, targetPackage);

		// We must be able to distinguish between plug-ins and features with the
		// same artifact ID, so keep two separate maps
		if (isFeature) {
			featureMetadataMap.put(am.getArtifactId(), am);
		} else {
			// We must be able to distinguish between different versions of the
			// same plug-in in the same reactor, so key by name_version
			try (JarFile bundle = new JarFile(am.getPath())) {
				// Also, the maven artifact version is likely to be different to the
				// osgi bundle version, so we must pull the version from the
				// manifest in order to be able to look it up correctly later
				Attributes attrs = bundle.getManifest().getMainAttributes();
				String version = attrs.getValue("Bundle-Version");
				pluginMetadataMap.put(am.getArtifactId() + "_" + version, am);
			} catch (IOException e) {
				throw new ArtifactInstallationException(
						"Unable to inspect bundle manifest", e);
			}
		}
	}

	@Override
	public void postInstallation() throws ArtifactInstallationException {
		try {
			Path tempRoot = Files.createTempDirectory("xmvn-root-");
			request.setBuildRoot(tempRoot);

			EclipseInstaller installer = equinox
					.getService(EclipseInstaller.class);
			EclipseInstallationResult result = installer
					.performInstallation(request);

			for (Dropin dropin : result.getDropins()) {
				JavaPackage pkg = packageMap.get(dropin.getId());
				addAllFiles(pkg, tempRoot.resolve(dropin.getPath()), tempRoot);

				for (Provide provide : dropin.getOsgiProvides()) {
					ArtifactMetadata am = null;
					if (provide.isFeature()) {
						am = featureMetadataMap.remove(provide.getId());
					} else {
						am = pluginMetadataMap.remove(provide.getId() + "_"
								+ provide.getVersion());
					}
					if (am == null) {
						logger.warn("Could not generate metadata for bundle: "
								+ provide.getId() + "_" + provide.getVersion());
						continue;
					}

					am.setPath(provide.getPath().toString());
					am.getProperties().putAll(provide.getProperties());
					am.getProperties().setProperty("osgi.id", provide.getId());
					am.getProperties().setProperty("osgi.version",
							provide.getVersion());

					pkg.getMetadata().addArtifact(am);
				}
			}
		} catch (Exception e) {
			throw new ArtifactInstallationException(
					"Unable to install Eclipse artifacts", e);
		}
	}

	private void addAllFiles(JavaPackage pkg, Path dropin, Path root)
			throws IOException {
		pkg.addFile(new Directory(root.relativize(dropin)));

		if (Files.isDirectory(dropin)) {
			for (Path path : Files.newDirectoryStream(dropin)) {
				Path relativePath = root.relativize(path);
				if (Files.isDirectory(path))
					addAllFiles(pkg, path, root);
				else {
					File f;

					if (Files.isSymbolicLink(path))
						f = new SymbolicLink(relativePath,
								Files.readSymbolicLink(path));
					else
						f = new RegularFile(relativePath, path);
					pkg.addFile(f);
				}
			}
		}
	}
}
