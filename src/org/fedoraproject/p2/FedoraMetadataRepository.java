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
package org.fedoraproject.p2;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.PublisherUtil;
import org.eclipse.equinox.internal.p2.updatesite.CategoryXMLAction;
import org.eclipse.equinox.internal.p2.updatesite.SiteCategory;
import org.eclipse.equinox.p2.core.IPool;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.IRepositoryReference;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.osgi.framework.BundleException;

public class FedoraMetadataRepository implements IMetadataRepository {

	private IProvisioningAgent agent;
	private File location;

	public FedoraMetadataRepository(IProvisioningAgent agent, File location) {
		this.agent = agent;
		this.location = location;
	}

	@Override
	public URI getLocation() {
		return location.toURI();
	}

	@Override
	public String getName() {
		return "Fedora Metadata Repository " + location.getAbsolutePath();
	}

	@Override
	public String getType() {
		return this.getClass().getName();
	}

	@Override
	public String getVersion() {
		return "0.0.1";
	}

	@Override
	public String getDescription() {
		return "Fedora p2 Metadata Repository";
	}

	@Override
	public String getProvider() {
		return "Fedora";
	}

	@Override
	public Map<String, String> getProperties() {
		return new HashMap<String, String> ();
	}

	@Override
	public String getProperty(String key) {
		return null;
	}

	@Override
	public IProvisioningAgent getProvisioningAgent() {
		return agent;
	}

	@Override
	public boolean isModifiable() {
		return false;
	}

	@Override
	public String setProperty(String key, String value) {
		return null;
	}

	@Override
	public String setProperty(String key, String value, IProgressMonitor monitor) {
		return null;
	}

	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public IQueryResult<IInstallableUnit> query(IQuery<IInstallableUnit> query,
			IProgressMonitor monitor) {
		return query.perform(getAllSystemIUs().iterator());
	}

	private Set<IInstallableUnit> getAllSystemIUs() {
		FeatureParser parser = new FeatureParser();
		Set<IInstallableUnit> allIUs = new HashSet<IInstallableUnit>();

		Collection<File> bundlePlugins = FedoraBundleIndex.getInstance().getAllBundles(new File(getLocation()), "osgi.bundle");
		Collection<File> bundleFeatures = FedoraBundleIndex.getInstance().getAllBundles(new File(getLocation()), "org.eclipse.update.feature");

		for (File bundleFile : bundlePlugins) {
			String id = "";
			String version = "";
			try {
				Dictionary<String, String> manifest = BundlesAction.loadManifest(bundleFile);
				id = manifest.get("Bundle-SymbolicName");
				version = manifest.get("Bundle-Version");
			} catch (IOException | BundleException e) {
			}
			IArtifactKey key = BundlesAction.createBundleArtifactKey(id, version);
			allIUs.add(PublisherUtil.createBundleIU(key, bundleFile));
		}

		for (File bundleFile : bundleFeatures) {
			IPublisherInfo info = new PublisherInfo();
			allIUs.add(FeaturesAction.createFeatureJarIU(parser.parse(bundleFile), info));
		}

//		allIUs.add(createRootCategory(allIUs));
		return allIUs;
	}

	private IInstallableUnit createRootCategory (Set<IInstallableUnit> ius) {
		CategoryXMLAction action = new CategoryXMLAction(getLocation(), "fedora");
		SiteCategory category = new SiteCategory();
		category.setLabel("All Content");
		category.setDescription("A category to aggregate all content at this location.");
		category.setName("org.fedoraproject.p2.category");
		return action.createCategoryIU(category, ius);
	}

	@Override
	public void addInstallableUnits(
			Collection<IInstallableUnit> installableUnits) {
	}

	@Override
	public void addReferences(
			Collection<? extends IRepositoryReference> references) {
	}

	@Override
	public Collection<IRepositoryReference> getReferences() {
		return Collections.EMPTY_LIST;
	}

	@Override
	public boolean removeInstallableUnits(
			Collection<IInstallableUnit> installableUnits) {
		return false;
	}

	@Override
	public void removeAll() {
	}

	@Override
	public IStatus executeBatch(IRunnableWithProgress runnable,
			IProgressMonitor monitor) {
		return null;
	}

	@Override
	public void compress(IPool<IInstallableUnit> iuPool) {
	}

}
