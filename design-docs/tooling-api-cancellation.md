## Feature: Tooling API client cancels a long running operation

Add possibility to cancell execution of a long running operation using a `CancallableToken`. 
This `CancallableToken` is produced by `CancellableTokenSource` and can be used with several operations if needed.

    interface CancellationToken {
        boolean canBeCancelled();
        boolean isCancellationRequested();
    }

    public class CancellationTokenSource {
        public void cancel() ...
        public CancellationToken token() ...
    }

    interface LongRunningOperation {
        ...
        @Incubating
        LongRunningOperation withCancellationToken(CancellationToken cancellationToken);
    }

In the API `CancellationTokenSource` is client side part with a factory method to create (provided part of the contract) `CancellationToken`. 
Client can pass this token to one or more operations and can call `cancel()` at any time.
The method does 'best-effort' to request stop for the performed operation assuming that the provider side cooperates and the implementation returns immediately.
To enable this cooperation operation implementation can query `CancellationToken.isCancellationRequested()`.
When provider successfully cancels the operation during its processing the client will be notified using `BuildCancelledException` passed to `ResultHandler.onFailure()` callback (another addition to API).
Provider ignores cancel requests after operation is finished.
Last call to 'LongRunningOperation.withCancellationToken` wins and each operation can use only one token.

Open questions:

Calling `cancel()` when the provider does not support it can 

- be a no-op (log that the request is ignored)
- throw an exception
- method can be changed to return boolean flag signaling if the cancel request was acknowledged.

### Story: Client uses internal API to request cancellation of long running operation

This story adds an API to allow a client to request the cancellation of a long running operation. For this story, the
API will be internal. The API will be made public in a later story.

#### Implementation plan

1. Add API to the client.
2. Add new tooling API protocol interfaces to support cancellation.
3. The provider implementation simply acknowledges the cancellation request, but does not actually cancel the operation. This is added in a
subsequent story.

#### Test cases

- Client can cancel operation for target Gradle version that supports cancellation:
    - Building model
    - Running tasks
    - Running build action
- Client receives reasonable error messages when attempting to cancel operation for target Gradle version that does not support cancellation.
- Client can cancel operation after operation has completed:
    - Successful operation
    - Failed operation
- Client cancels operation from `ResultHandler`
- Client can cancel operation before its start and it won't be executed:
    - Building model
    - Running tasks
    - Running build action

#### Open issues

- Behaviour when cancellation is not supported.

### Story: Daemon exits when operation is cancelled

This story adds a basic cancellation implementation. The behaviour is similar to the case where the command-line client is killed, where the daemon
process simply exits. This behaviour will be improved later.

#### Implementation plan

1. Provider implementation forwards the cancellation request to the daemon.
2. The daemon uses `DaemonStateControl.requestForcefulStop()` to terminate the build. (This is achieved by calling `DaemonClient.stop()` from toolingApi provider/daemon client.)
3. Forward a 'build cancelled' exception to the client.

#### Test cases

- Client cancels a long build
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is no longer running.

### Story: Daemon waits short period of time for cancelled operation to complete

In this story, the daemon attempts to terminate a cancelled build more gracefully, by waiting for a short period of time for the build to complete.

#### Implementation plan

1. When `DaemonStateControl.requestForcefulStop()` is called, the daemon waits for 10 seconds (say) for the build to complete. If the build
completes, then do not exit. If the build does not complete in this time, exit the process.

#### Test cases

- Client cancels a short build
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is still running.
    - Should use fixture to probe the daemon logs to determine that it has seen the request and terminated the build cleanly.
    - Client continues to get build output during this time.
- Command-line client runs a short build and is killed.
    - Some time after this the daemon finishes the build and continues to run.
    - Should use fixture to probe the daemon logs to determine that it has seen the event and terminated the build cleanly.
- Extend the existing daemon termination tests to use fixture to probe the daemon logs to determine that the daemon has decided to exit.

### Story: Task graph execution is aborted when operation is cancelled

In this story, task graph executor no longer starts executing new tasks when operation is cancelled.

#### Test cases

- Client cancels a build before the start of some task(s) execution
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is still running and the task is not executed.

### Story: Gradle distribution download is aborted when operation is cancelled

In this story, the Gradle distribution download is stopped when operation is cancelled.

#### Test cases

- Client requests to execute an operation (build, model) using a distribution that needs to be downloaded and cancels the operation during the download
    - Some time after requesting, the client receives a 'build cancelled' exception and download is terminated and partial downloads are removed
    - Verify this behavior for regularly processed downloads and for stalled downloads waiting on blocking I/O.

### Story: Project configuration is aborted when operation is cancelled

In this story, no further projects should be configured after the operation is cancelled. Any project configuration
action that is currently executing should continue, similar to the task graph exececution.

Implementation-wise, change `DefaultBuildConfigurer` to stop configuring projects one the cancellation
token has been activated.

### Story: Tooling API client receives BuildCancelledException as the result of a cancelled operation

This story ensures that consistent behaviour is seen by the client as the result of a cancelled operation.

### Story: Build action receives exception when operation is cancelled

In this story, a `BuildAction` receives an exception when it is using or uses a method on `BuildController` when operation is cancelled.

### Story: Make cancellation API public

Add to public API and document.

## Later stories

### Story: Tooling API client receives feedback after cancellation is requested

Add some mechanism to inform the client when cancellation is or is not available, and also to inform 
the client about the state of a cancellation request.

### Story: Task graph assembly is aborted when operation is cancelled

### Story: Model rule execution is aborted when operation is cancelled

In this story, the `ModelRegistry` implementation stops executing rules when operation is cancelled.

### Story: Nested operations started using tooling API are cancelled when outer operation is cancelled

When build logic uses the tooling API to start further operations, these nested operations should also be cancelled.
