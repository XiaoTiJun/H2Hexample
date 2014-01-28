package org.hive2hive.processes;

import java.io.File;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.Set;

import org.hive2hive.core.H2HSession;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.process.login.SessionParameters;
import org.hive2hive.core.process.notify.BaseNotificationMessageFactory;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.processes.framework.abstracts.ProcessComponent;
import org.hive2hive.processes.framework.concretes.SequentialProcess;
import org.hive2hive.processes.framework.decorators.AsyncComponent;
import org.hive2hive.processes.framework.decorators.AsyncResultComponent;
import org.hive2hive.processes.framework.interfaces.IProcessComponent;
import org.hive2hive.processes.framework.interfaces.IResultProcessComponent;
import org.hive2hive.processes.implementations.common.File2MetaFileComponent;
import org.hive2hive.processes.implementations.common.GetUserLocationsStep;
import org.hive2hive.processes.implementations.common.PutMetaDocumentStep;
import org.hive2hive.processes.implementations.common.PutUserLocationsStep;
import org.hive2hive.processes.implementations.common.userprofiletask.GetUserProfileTaskStep;
import org.hive2hive.processes.implementations.context.AddFileProcessContext;
import org.hive2hive.processes.implementations.context.DeleteFileProcessContext;
import org.hive2hive.processes.implementations.context.DownloadFileContext;
import org.hive2hive.processes.implementations.context.LoginProcessContext;
import org.hive2hive.processes.implementations.context.LogoutProcessContext;
import org.hive2hive.processes.implementations.context.NotifyProcessContext;
import org.hive2hive.processes.implementations.context.RegisterProcessContext;
import org.hive2hive.processes.implementations.context.UpdateFileProcessContext;
import org.hive2hive.processes.implementations.context.UserProfileTaskContext;
import org.hive2hive.processes.implementations.context.interfaces.IConsumeNotificationFactory;
import org.hive2hive.processes.implementations.files.add.AddToUserProfileStep;
import org.hive2hive.processes.implementations.files.add.CreateMetaDocumentStep;
import org.hive2hive.processes.implementations.files.add.GetParentMetaStep;
import org.hive2hive.processes.implementations.files.add.PrepareNotificationStep;
import org.hive2hive.processes.implementations.files.add.PutChunksStep;
import org.hive2hive.processes.implementations.files.add.UpdateParentMetaStep;
import org.hive2hive.processes.implementations.files.delete.DeleteChunksProcess;
import org.hive2hive.processes.implementations.files.delete.DeleteFileOnDiskStep;
import org.hive2hive.processes.implementations.files.download.FindInUserProfileStep;
import org.hive2hive.processes.implementations.files.list.GetFileListStep;
import org.hive2hive.processes.implementations.files.update.CreateNewVersionStep;
import org.hive2hive.processes.implementations.files.update.DeleteChunksStep;
import org.hive2hive.processes.implementations.files.update.UpdateMD5inUserProfileStep;
import org.hive2hive.processes.implementations.login.ContactOtherClientsStep;
import org.hive2hive.processes.implementations.login.GetUserProfileStep;
import org.hive2hive.processes.implementations.login.SessionCreationStep;
import org.hive2hive.processes.implementations.login.SynchronizeFilesStep;
import org.hive2hive.processes.implementations.logout.RemoveOwnLocationsStep;
import org.hive2hive.processes.implementations.notify.GetAllLocationsStep;
import org.hive2hive.processes.implementations.notify.GetPublicKeysStep;
import org.hive2hive.processes.implementations.notify.PutAllUserProfileTasksStep;
import org.hive2hive.processes.implementations.notify.RemoveUnreachableStep;
import org.hive2hive.processes.implementations.notify.SendNotificationsMessageStep;
import org.hive2hive.processes.implementations.notify.VerifyNotificationFactoryStep;
import org.hive2hive.processes.implementations.register.AssureUserInexistentStep;
import org.hive2hive.processes.implementations.register.PutPublicKeyStep;
import org.hive2hive.processes.implementations.register.PutUserProfileStep;
import org.hive2hive.processes.implementations.userprofiletask.HandleUserProfileTaskStep;

public final class ProcessFactory {

	private static ProcessFactory instance;

	public static ProcessFactory instance() {
		if (instance == null)
			instance = new ProcessFactory();
		return instance;
	}

	private ProcessFactory() {
		// singleton
	}

	public IProcessComponent createRegisterProcess(UserCredentials credentials, NetworkManager networkManager) {
		UserProfile profile = new UserProfile(credentials.getUserId());
		RegisterProcessContext context = new RegisterProcessContext(profile);

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new AssureUserInexistentStep(credentials.getUserId(), context, networkManager));
		process.add(new AsyncComponent(new PutUserProfileStep(credentials, profile, networkManager)));
		process.add(new AsyncComponent(new PutUserLocationsStep(context, context, networkManager)));
		process.add(new AsyncComponent(new PutPublicKeyStep(profile, networkManager)));

		AsyncComponent registerProcess = new AsyncComponent(process);

		return registerProcess;
	}

	public IProcessComponent createLoginProcess(UserCredentials credentials, SessionParameters params,
			NetworkManager networkManager) {

		LoginProcessContext context = new LoginProcessContext();

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new GetUserProfileStep(credentials, context, networkManager));
		process.add(new SessionCreationStep(params, context, networkManager));
		process.add(new GetUserLocationsStep(credentials.getUserId(), context, networkManager));
		process.add(new ContactOtherClientsStep(context, networkManager));
		process.add(new PutUserLocationsStep(context, context, networkManager));
		process.add(new SynchronizeFilesStep(context));

		// TODO add user profile task step

		AsyncComponent loginProcess = new AsyncComponent(process);

		return loginProcess;
	}

	public ProcessComponent createUserProfileTaskStep(NetworkManager networkManager) {
		SequentialProcess process = new SequentialProcess();

		UserProfileTaskContext context = new UserProfileTaskContext();

		process.add(new GetUserProfileTaskStep(context, networkManager));
		// Note: this step will add the next steps since it depends on the get result
		process.add(new HandleUserProfileTaskStep(context, networkManager));

		return process;
	}

	public IProcessComponent createLogoutProcess(H2HSession session, NetworkManager networkManager) {

		LogoutProcessContext context = new LogoutProcessContext(session);

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new GetUserLocationsStep(session.getCredentials().getUserId(), context, networkManager));
		process.add(new RemoveOwnLocationsStep(context, networkManager));

		AsyncComponent logoutProcess = new AsyncComponent(process);

		return logoutProcess;
	}

	public IProcessComponent createNewFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException {

		Path root = networkManager.getSession().getFileManager().getRoot();
		boolean inRoot = root.equals(file.toPath().getParent());

		AddFileProcessContext context = new AddFileProcessContext(file, inRoot, networkManager.getSession());

		SequentialProcess process = new SequentialProcess();
		process.add(new GetParentMetaStep(context, networkManager));
		process.add(new PutChunksStep(context, networkManager));
		process.add(new CreateMetaDocumentStep(context));
		process.add(new PutMetaDocumentStep(context, context, networkManager));

		if (!inRoot) {
			// need to update the parent if necessary
			process.add(new UpdateParentMetaStep(context, networkManager));
		}

		process.add(new AddToUserProfileStep(context));
		process.add(new PrepareNotificationStep(context));
		process.add(createNotificationProcess(context, networkManager));

		AsyncComponent addFileProcess = new AsyncComponent(process);

		return addFileProcess;
	}

	public IProcessComponent createUpdateFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException, IllegalArgumentException {
		if (!file.isFile()) {
			throw new IllegalArgumentException("A folder can have one version only");
		}

		Path root = networkManager.getSession().getFileManager().getRoot();
		boolean inRoot = root.equals(file.toPath().getParent());

		UpdateFileProcessContext context = new UpdateFileProcessContext(file, inRoot,
				networkManager.getSession());

		SequentialProcess process = new SequentialProcess();
		process.add(new File2MetaFileComponent(file, context, context, networkManager));
		process.add(new PutChunksStep(context, networkManager));
		process.add(new CreateNewVersionStep(context));
		process.add(new PutMetaDocumentStep(context, context, networkManager));
		process.add(new UpdateMD5inUserProfileStep(context));

		// TODO: cleanup can be made async because user operation does not depend on it
		process.add(new DeleteChunksStep(context, networkManager));
		if (!inRoot) {
			process.add(new GetParentMetaStep(context, networkManager));
		}
		process.add(new PrepareNotificationStep(context));
		process.add(createNotificationProcess(context, networkManager));

		AsyncComponent updateFileProcess = new AsyncComponent(process);

		return updateFileProcess;
	}

	public IProcessComponent createDownloadFileProcess(PublicKey fileKey, NetworkManager networkManager) {
		SequentialProcess process = new SequentialProcess();

		DownloadFileContext context = new DownloadFileContext(fileKey);
		process.add(new FindInUserProfileStep(context, networkManager));

		return process;
	}

	public IProcessComponent createDeleteFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException {

		// TODO is this process even necessary for folders?

		DeleteFileProcessContext context = new DeleteFileProcessContext(file.isDirectory());

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new DeleteFileOnDiskStep(file)); // TODO make asynchronous
		process.add(new File2MetaFileComponent(file, context, context, networkManager));
		process.add(new DeleteChunksProcess(context, networkManager));

		return process;
	}

	public IResultProcessComponent<List<Path>> createFileListProcess(NetworkManager networkManager) {

		GetFileListStep listStep = new GetFileListStep(networkManager);

		return new AsyncResultComponent<List<Path>>(listStep);
	}

	public IProcessComponent createNotificationProcess(final BaseNotificationMessageFactory messageFactory,
			final Set<String> usersToNotify, NetworkManager networkManager) {
		// create a context here to provide the necessary data
		IConsumeNotificationFactory context = new IConsumeNotificationFactory() {

			@Override
			public Set<String> consumeUsersToNotify() {
				return usersToNotify;
			}

			@Override
			public BaseNotificationMessageFactory consumeMessageFactory() {
				return messageFactory;
			}
		};
		return createNotificationProcess(context, networkManager);
	}

	private ProcessComponent createNotificationProcess(IConsumeNotificationFactory providerContext,
			NetworkManager networkManager) throws IllegalArgumentException {
		NotifyProcessContext context = new NotifyProcessContext(providerContext);

		SequentialProcess process = new SequentialProcess();
		process.add(new VerifyNotificationFactoryStep(context, networkManager.getUserId()));
		process.add(new GetPublicKeysStep(context, networkManager));
		process.add(new PutAllUserProfileTasksStep(context, networkManager));
		process.add(new GetAllLocationsStep(context, networkManager));
		process.add(new SendNotificationsMessageStep(context, networkManager));
		// cleanup my own locations
		process.add(new GetUserLocationsStep(networkManager.getUserId(), context, networkManager));
		process.add(new RemoveUnreachableStep(context, networkManager));

		return process;
	}
}
