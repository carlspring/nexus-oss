/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2014 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.storage.local.fs;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.mime.MimeSupport;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.attributes.Attributes;
import org.sonatype.nexus.proxy.item.AbstractStorageItem;
import org.sonatype.nexus.proxy.item.ByteArrayContentLocator;
import org.sonatype.nexus.proxy.item.ContentLocator;
import org.sonatype.nexus.proxy.item.DefaultStorageCollectionItem;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.DefaultStorageLinkItem;
import org.sonatype.nexus.proxy.item.FileContentLocator;
import org.sonatype.nexus.proxy.item.LinkPersister;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.StorageLinkItem;
import org.sonatype.nexus.proxy.item.uid.IsItemAttributeMetacontentAttribute;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.proxy.storage.local.AbstractLocalRepositoryStorage;
import org.sonatype.nexus.proxy.storage.local.LocalStorageContext;
import org.sonatype.nexus.proxy.utils.RepositoryStringUtils;
import org.sonatype.nexus.proxy.wastebasket.Wastebasket;
import org.sonatype.nexus.util.PathUtils;
import org.sonatype.nexus.util.file.DirSupport;

import com.google.common.base.Strings;
import org.apache.commons.io.IOUtils;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.proxy.ItemNotFoundException.reasonFor;

/**
 * LocalRepositoryStorage that uses plain File System (relies on {@link File}) to implement it's functionality.
 *
 * @author cstamas
 */
@Singleton
@Named(DefaultFSLocalRepositoryStorage.PROVIDER_STRING)
public class DefaultFSLocalRepositoryStorage
    extends AbstractLocalRepositoryStorage
{
  public static final String PROVIDER_STRING = "file";

  /**
   * Key of the {@link File} denoting repository root directory in repository's context.
   */
  private static final String BASEDIR_FILE = DefaultFSLocalRepositoryStorage.class.getName() + ".baseDir";

  private FSPeer fsPeer;

  @Inject
  public DefaultFSLocalRepositoryStorage(final Wastebasket wastebasket, final LinkPersister linkPersister,
                                         final MimeSupport mimeSupport, final FSPeer fsPeer)
  {
    super(wastebasket, linkPersister, mimeSupport);
    this.fsPeer = checkNotNull(fsPeer);
  }

  protected FSPeer getFSPeer() {
    return fsPeer;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_STRING;
  }

  @Override
  public void validateStorageUrl(String url)
      throws LocalStorageException
  {
    boolean result = validFileUrl(url);

    if (!result) {
      throw new LocalStorageException("Invalid storage URL, not a file based one: " + url);
    }
  }

  @Override
  protected void updateContext(final Repository repository, final LocalStorageContext context)
      throws IOException
  {
    final File file = getFileFromUrl(repository.getLocalUrl());
    DirSupport.mkdir(file.toPath());
    context.putContextObject(BASEDIR_FILE, file);
  }


  /**
   * Gets the base dir.
   *
   * @return the base dir
   */
  public File getBaseDir(final Repository repository, final ResourceStoreRequest request)
      throws LocalStorageException
  {
    return (File) getLocalStorageContext(repository).getContextObject(BASEDIR_FILE);
  }

  /**
   * Gets the file from base.
   *
   * @return the file from base
   */
  public File getFileFromBase(final Repository repository, final ResourceStoreRequest request, final File repoBase)
      throws LocalStorageException
  {
    File result = null;

    if (request.getRequestPath() == null || RepositoryItemUid.PATH_ROOT.equals(request.getRequestPath())) {
      result = repoBase;
    }
    else if (request.getRequestPath().startsWith("/")) {
      result = new File(repoBase, request.getRequestPath().substring(1));
    }
    else {
      result = new File(repoBase, request.getRequestPath());
    }

    if (log.isTraceEnabled()) {
      log.trace("{} --> {}", request.getRequestPath(), result.getAbsoluteFile());
    }

    // to be foolproof, chrooting it
    if (!result.getAbsolutePath().startsWith(getBaseDir(repository, request).getAbsolutePath())) {
      throw new LocalStorageException("getFileFromBase() method evaluated directory wrongly in repository \""
          + repository.getName() + "\" (id=\"" + repository.getId() + "\")! baseDir="
          + getBaseDir(repository, request).getAbsolutePath() + ", target=" + result.getAbsolutePath());
    }
    else {
      return result;
    }
  }

  /**
   * Gets the file from base.
   *
   * @return the file from base
   */
  public File getFileFromBase(Repository repository, ResourceStoreRequest request)
      throws LocalStorageException
  {
    return getFileFromBase(repository, request, getBaseDir(repository, request));
  }

  /**
   * Retrieve item from file.
   */
  protected AbstractStorageItem retrieveItemFromFile(Repository repository, ResourceStoreRequest request, File target)
      throws ItemNotFoundException, LocalStorageException
  {
    String path = request.getRequestPath();

    boolean mustBeACollection = path.endsWith(RepositoryItemUid.PATH_SEPARATOR);

    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    if (Strings.isNullOrEmpty(path)) {
      path = RepositoryItemUid.PATH_ROOT;
    }

    final RepositoryItemUid uid = repository.createUid(path);

    final AbstractStorageItem result;
    if (target.isDirectory()) {
      request.setRequestPath(path);

      DefaultStorageCollectionItem coll =
          new DefaultStorageCollectionItem(repository, request, target.canRead(), target.canWrite());
      coll.setModified(target.lastModified());
      coll.setCreated(target.lastModified());
      result = coll;
    }
    else if (target.isFile() && !mustBeACollection) {
      request.setRequestPath(path);

      // FileComtentLocator is reusable, so create it only once but with correct MIME type
      final FileContentLocator fileContent = new FileContentLocator(target, getMimeSupport().guessMimeTypeFromPath(
          repository.getMimeRulesSource(), target.getAbsolutePath()));

      try {
        // Probe for link only if we KNOW it's not an attribute but "plain" content
        final boolean isAttribute = uid.getBooleanAttributeValue(IsItemAttributeMetacontentAttribute.class);
        final boolean isLink = !isAttribute && getLinkPersister().isLinkContent(fileContent);
        if (isLink) {
          try {
            DefaultStorageLinkItem link =
                new DefaultStorageLinkItem(repository, request, target.canRead(), target.canWrite(),
                    getLinkPersister().readLinkContent(fileContent));
            repository.getAttributesHandler().fetchAttributes(link);
            link.setModified(target.lastModified());
            link.setCreated(target.lastModified());
            result = link;

            repository.getAttributesHandler().touchItemLastRequested(System.currentTimeMillis(), link);
          }
          catch (NoSuchRepositoryException e) {
            log.warn("Stale link object found on UID: {}, deleting it.", uid);
            DirSupport.delete(target.toPath());
            throw new ItemNotFoundException(reasonFor(request, repository,
                "Path %s not found in local storage of repository %s", request.getRequestPath(),
                RepositoryStringUtils.getHumanizedNameString(repository)), e);
          }
        }
        else {
          DefaultStorageFileItem file =
              new DefaultStorageFileItem(repository, request, target.canRead(), target.canWrite(),
                  fileContent);
          repository.getAttributesHandler().fetchAttributes(file);
          file.setModified(target.lastModified());
          file.setCreated(target.lastModified());
          result = file;

          repository.getAttributesHandler().touchItemLastRequested(System.currentTimeMillis(), file);
        }
      }
      catch (FileNotFoundException e) {
        // It is possible for this file to have been removed after the call to target.exists()
        // this could have been an external process
        // See: https://issues.sonatype.org/browse/NEXUS-4570
        log.debug("File '{}' removed before finished processing the directory listing", target, e);
        throw new ItemNotFoundException(reasonFor(request, repository,
            "Path %s not found in local storage of repository %s", request.getRequestPath(),
            RepositoryStringUtils.getHumanizedNameString(repository)), e);
      }
      catch (IOException e) {
        throw new LocalStorageException("Exception during reading up an item from FS storage!", e);
      }
    }
    else {
      throw new ItemNotFoundException(reasonFor(request, repository,
          "Path %s not found in local storage of repository %s", request.getRequestPath(),
          RepositoryStringUtils.getHumanizedNameString(repository)));
    }

    return result;
  }

  public boolean isReachable(Repository repository, ResourceStoreRequest request)
      throws LocalStorageException
  {
    final File target = getBaseDir(repository, request);
    return getFSPeer().isReachable(repository, target, request, target);
  }

  public boolean containsItem(Repository repository, ResourceStoreRequest request)
      throws LocalStorageException
  {
    return getFSPeer()
        .containsItem(repository, getBaseDir(repository, request), request, getFileFromBase(repository, request));
  }

  public AbstractStorageItem retrieveItem(Repository repository, ResourceStoreRequest request)
      throws ItemNotFoundException, LocalStorageException
  {
    return retrieveItemFromFile(repository, request, getFileFromBase(repository, request));
  }

  public void storeItem(Repository repository, StorageItem item)
      throws UnsupportedStorageOperationException, LocalStorageException
  {
    final File target;
    final ContentLocator originalContentLocator;
    if (item instanceof StorageFileItem) {
      originalContentLocator = ((StorageFileItem) item).getContentLocator();
    }
    else {
      originalContentLocator = null;
    }
    try {
      // set some sanity stuff
      item.setStoredLocally(System.currentTimeMillis());
      item.setRemoteChecked(item.getStoredLocally());
      item.setExpired(false);

      ContentLocator cl = null;

      if (item instanceof StorageFileItem) {
        StorageFileItem fItem = (StorageFileItem) item;

        prepareStorageFileItemForStore(fItem);

        cl = fItem.getContentLocator();
      }
      else if (item instanceof StorageLinkItem) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
          getLinkPersister().writeLinkContent((StorageLinkItem) item, bos);
        }
        catch (IOException e) {
          // should not happen, look at implementation
          // we will handle here two byte array backed streams!
          throw new LocalStorageException("Problem ", e);
        }

        cl = new ByteArrayContentLocator(bos.toByteArray(), "text/xml");
      }

      target = getFileFromBase(repository, item.getResourceStoreRequest());

      getFSPeer().storeItem(repository, getBaseDir(repository, item.getResourceStoreRequest()), item, target, cl);
    }
    finally {
      // NEXUS-5468: Ensure that in case of file item with prepared content
      // (typically those coming from RRS, as the content is actually wrapped HTTP response body, hence not reusable)
      // get closed irrelevant of the actual outcome. If all went right, stream was already closed,
      // and we will be "punished" by one extra (redundant) call to Closeable#close().
      if (originalContentLocator instanceof Closeable) {
        IOUtils.closeQuietly((Closeable) originalContentLocator);
      }
    }

    if (item instanceof StorageFileItem) {
      // replace content locator transparently, if we just consumed a non-reusable one
      // Hint: in general, those items coming from user uploads or remote proxy caching requests are non
      // reusable ones
      ((StorageFileItem) item).setContentLocator(new FileContentLocator(target,
          ((StorageFileItem) item).getMimeType()));
    }

    final ContentLocator mdis =
        item instanceof StorageFileItem ? ((StorageFileItem) item).getContentLocator() : null;

    try {
      repository.getAttributesHandler().storeAttributes(item, mdis);
    }
    catch (IOException e) {
      throw new LocalStorageException("Cannot store attributes!", e);
    }
  }

  public void shredItem(Repository repository, ResourceStoreRequest request)
      throws ItemNotFoundException, UnsupportedStorageOperationException, LocalStorageException
  {
    RepositoryItemUid uid = repository.createUid(request.getRequestPath());

    try {
      repository.getAttributesHandler().deleteAttributes(uid);
    }
    catch (IOException e) {
      throw new LocalStorageException("Cannot delete attributes!", e);
    }

    File target = getFileFromBase(repository, request);

    getFSPeer().shredItem(repository, getBaseDir(repository, request), request, target);
  }

  public void moveItem(Repository repository, ResourceStoreRequest from, ResourceStoreRequest to)
      throws ItemNotFoundException, UnsupportedStorageOperationException, LocalStorageException
  {
    RepositoryItemUid fromUid = repository.createUid(from.getRequestPath());

    try {
      Attributes fromAttr = repository.getAttributesHandler().getAttributeStorage().getAttributes(fromUid);

      // check does it have attrs at all
      if (fromAttr != null) {
        RepositoryItemUid toUid = repository.createUid(to.getRequestPath());
        fromAttr.setRepositoryId(toUid.getRepository().getId());
        fromAttr.setPath(toUid.getPath());
        repository.getAttributesHandler().getAttributeStorage().putAttributes(toUid, fromAttr);
      }

      File fromTarget = getFileFromBase(repository, from);

      File toTarget = getFileFromBase(repository, to);

      getFSPeer().moveItem(repository, getBaseDir(repository, from), from, fromTarget, to, toTarget);

      repository.getAttributesHandler().getAttributeStorage().deleteAttributes(fromUid);
    }
    catch (LocalStorageException e) {
      // to not wrap these, they are IOEx subclass
      throw e;
    }
    catch (IOException e) {
      // cleanup
      throw new LocalStorageException("Cannot store attributes!", e);
    }
  }

  public Collection<StorageItem> listItems(Repository repository, ResourceStoreRequest request)
      throws ItemNotFoundException, LocalStorageException
  {
    List<StorageItem> result = new ArrayList<StorageItem>();

    File target = getFileFromBase(repository, request);

    Collection<File> files = getFSPeer().listItems(repository, getBaseDir(repository, request), request, target);

    if (files != null) {
      for (File file : files) {
        String newPath = PathUtils.concatPaths(request.getRequestPath(), file.getName());

        request.pushRequestPath(newPath);
        try {
          ResourceStoreRequest collMemberReq = new ResourceStoreRequest(request);
          try {
            result.add(retrieveItemFromFile(repository, collMemberReq, file));
          }
          catch (ItemNotFoundException e) {
            log.debug("ItemNotFoundException while listing directory, for request: {}",
                collMemberReq.getRequestPath(), e);
          }
        }
        finally {
          request.popRequestPath();
        }
      }
    }
    else {
      result.add(retrieveItemFromFile(repository, request, target));
    }

    return result;
  }

  private static File getFileFromUrl(String urlPath) {
    if (validFileUrl(urlPath)) {
      try {
        URL url = new URL(urlPath);
        try {
          return new File(url.toURI());
        }
        catch (Exception t) {
          return new File(url.getPath());
        }
      }
      catch (MalformedURLException e) {
        // Try just a regular file
        return new File(urlPath);
      }
    }

    return null;
  }

  private static boolean validFileUrl(String url) {
    boolean result = true;

    if (!validFile(new File(url))) {
      // Failed w/ straight file, now time to try URL
      try {
        if (!validFile(new File(new URL(url).getFile()))) {
          result = false;
        }
      }
      catch (MalformedURLException e) {
        result = false;
      }
    }

    return result;
  }

  private static Set<File> roots = null;

  private static boolean validFile(File file) {
    if (roots == null) {
      roots = new HashSet<>();

      File[] listedRoots = File.listRoots();

      for (int i = 0; i < listedRoots.length; i++) {
        roots.add(listedRoots[i]);
      }

      // Allow UNC based paths on windows
      // i.e. \\someserver\repository\central\blah
      if (isWindows()) {
        roots.add(new File("\\\\"));
      }
    }

    File root = file;

    while (root.getParentFile() != null) {
      root = root.getParentFile();
    }

    return roots.contains(root);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").contains("Windows");
  }

}
