package com.cafebazaar.servicebase

import android.content.Context
import android.os.Looper
import com.cafebazaar.servicebase.communicator.ClientConnectionCommunicator
import com.cafebazaar.servicebase.communicator.ClientReceiverCommunicator
import com.cafebazaar.servicebase.receiver.ClientReceiver
import com.cafebazaar.servicebase.state.ClientError
import com.cafebazaar.servicebase.state.ClientStateListener

abstract class Client(private val context: Context) {

    private var clientStateListener: ClientStateListener? = null

    protected abstract val supportedClientVersion: Long
    protected abstract fun getServiceConnection(): ClientConnectionCommunicator?
    protected abstract fun getBroadcastConnections(): ClientConnectionCommunicator?

    @Volatile private var clientState = DISCONNECTED
    @Volatile protected var clientConnection: ClientConnectionCommunicator? = null

    private val isReady: Boolean
        get() = (clientState == CONNECTED)
            .and(clientConnection != null)

    fun startConnection(clientStateListener: ClientStateListener) {
        this.clientStateListener = clientStateListener
        if (isNotBazaarInstalled(clientStateListener)) return
        if (isNotBazaarCompatible(clientStateListener)) return
        throwExceptionIfRunningOnMainThread()
        startingConnection(clientStateListener)
    }

    private fun isNotBazaarCompatible(clientStateListener: ClientStateListener): Boolean {
        if (getBazaarVersionCode(context) < supportedClientVersion) {
            handleErrorOnBazaarIsNotCompatible(clientStateListener)
            return true
        }
        return false
    }

    private fun isNotBazaarInstalled(clientStateListener: ClientStateListener): Boolean {
        if (verifyBazaarIsInstalled(context).not()) {
            handleErrorOnBazaarIsNotInstalled(clientStateListener)
            return true
        }
        return false
    }

    private fun handleErrorOnBazaarIsNotCompatible(clientStateListener: ClientStateListener) {
        clientState = DISCONNECTED
        clientStateListener.onError(
            ClientError.ERROR_BAZAAR_IS_NOT_COMPATIBLE
        )
    }

    private fun handleErrorOnBazaarIsNotInstalled(clientStateListener: ClientStateListener) {
        clientState = DISCONNECTED
        clientStateListener.onError(
            ClientError.ERROR_BAZAAR_IS_NOT_INSTALL
        )
    }

    private fun startingConnection(clientStateListener: ClientStateListener) {
        if (isReady.not()) {
            if (clientState == CONNECTING) {
                clientStateListener.onError(
                    ClientError.ERROR_SDK_IS_STARTED
                )
                return
            }
            tryToConnect(clientStateListener)
        } else {
            clientStateListener.onReady()
        }
    }

    private fun tryToConnect(clientStateListener: ClientStateListener) {
        clientState = CONNECTING
        if (tryConnectingByService(clientStateListener)) return
        if (tryConnectingByBroadcast(clientStateListener)) return
        clientState = DISCONNECTED
        clientStateListener.onError(
            ClientError.ERROR_SDK_COULD_NOT_CONNECT
        )
    }

    private fun tryConnectingByBroadcast(clientStateListener: ClientStateListener): Boolean {
        getBroadcastConnections()?.let { connection ->
            if (connection is ClientReceiverCommunicator) {
                ClientReceiver.addObserver(connection)
            }
            if (connection.startConnection()) {
                clientConnection = connection
                clientState = CONNECTED
                clientStateListener.onReady()
                return true
            }
        }
        return false
    }

    private fun tryConnectingByService(clientStateListener: ClientStateListener): Boolean {
        getServiceConnection()?.let { connection ->
            if (connection.startConnection()) {
                clientConnection = connection
                clientState = CONNECTED
                clientStateListener.onReady()
                return true
            }
        }
        return false
    }

    private fun throwExceptionIfRunningOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalThreadStateException(OFF_MAIN_THREAD_EXCEPTION)
        }
    }

    fun endConnection() {
        clientStateListener = null
        clientConnection?.let { connection ->
            if (connection is ClientReceiverCommunicator) {
                ClientReceiver.removeObserver(connection)
            }
            connection.stopConnection()
        }
        clientState = DISCONNECTED
    }

    protected fun errorOccurred(clientError: ClientError) {
        clientStateListener?.onError(clientError)
    }

    @Throws(IllegalStateException::class)
    protected fun <T> runIfReady(block: () -> T?): T? {
        if (isReady.not()) {
            throw IllegalStateException(SERVICE_IS_NOT_STARTED_EXCEPTION)
        } else {
            return block.invoke()
        }
    }

    private fun verifyBazaarIsInstalled(context: Context): Boolean {
        return getBazaarPackageInfo(context) != null
    }

    companion object {
        const val SERVICE_PACKAGE_NAME = "com.farsitel.bazaar"

        private const val OFF_MAIN_THREAD_EXCEPTION = "This function has to call off the main thread."
        private const val SERVICE_IS_NOT_STARTED_EXCEPTION =
            "Service not connected. Please start a connection before using the service."
        private const val DISCONNECTED = 0
        private const val CONNECTING = 1
        private const val CONNECTED = 2
    }
}