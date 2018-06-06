/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Implemented by services that want to implement remote resource management.
 *
 * <ul>
 * <li>{@link CloudletInterface#doGet} is used to implement a READ request for a resource identified by the supplied
 * {@link CloudletResources)}
 * <li>{@link CloudletInterface#doPut} is used to implement a CREATE or UPDATE request for a resource identified by the
 * supplied {@link CloudletResources}
 * <li>{@link CloudletInterface#doDel} is used to implement a DELETE request for a resource identified by the supplied
 * {@link CloudletResources}
 * <li>{@link CloudletInterface#doPost} is used to implement other operations on a resource identified by the supplied
 * {@link CloudletResources}
 * <li>{@link CloudletInterface#doExec} is used to perform application operation not necessary tied to a given resource.
 * </ul>
 * 
 * @since 2.0
 */
@ConsumerType
public interface CloudletInterface {

    /**
     * Used to implement a READ request for a resource identified by the supplied {@link CloudletResources)}
     * 
     * @param reqResources
     *            resources associated with the request received
     * @param reqPayload
     *            represents as a {@link KuraPayload} the received message
     * @return the response to be provided back as {@link KuraPayload}
     * @throws KuraException
     *             An exception is thrown in every condition where the request cannot be full fitted due to wrong
     *             request parameters or exceptions during processing
     */
    public default KuraPayload doGet(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    /**
     * Used to implement a CREATE or UPDATE request for a resource identified by the supplied {@link CloudletResources)}
     * 
     * @param reqResources
     *            resources associated with the request received
     * @param reqPayload
     *            represents as a {@link KuraPayload} the received message
     * @return the response to be provided back as {@link KuraPayload}
     * @throws KuraException
     *             An exception is thrown in every condition where the request cannot be full fitted due to wrong
     *             request parameters or exceptions during processing
     */
    public default KuraPayload doPut(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    /**
     * Used to implement other operations on a resource identified by the supplied {@link CloudletResources)}
     * 
     * @param reqResources
     *            resources associated with the request received
     * @param reqPayload
     *            represents as a {@link KuraPayload} the received message
     * @return the response to be provided back as {@link KuraPayload}
     * @throws KuraException
     *             An exception is thrown in every condition where the request cannot be full fitted due to wrong
     *             request parameters or exceptions during processing
     */
    public default KuraPayload doPost(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    /**
     * Used to implement a DELETE request for a resource identified by the supplied {@link CloudletResources)}
     * 
     * @param reqResources
     *            resources associated with the request received
     * @param reqPayload
     *            represents as a {@link KuraPayload} the received message
     * @return the response to be provided back as {@link KuraPayload}
     * @throws KuraException
     *             An exception is thrown in every condition where the request cannot be full fitted due to wrong
     *             request parameters or exceptions during processing
     */
    public default KuraPayload doDel(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

    /**
     * Uused to perform application operation not necessary tied to a given resource
     * 
     * @param reqResources
     *            resources associated with the request received
     * @param reqPayload
     *            represents as a {@link KuraPayload} the received message
     * @return the response to be provided back as {@link KuraPayload}
     * @throws KuraException
     *             An exception is thrown in every condition where the request cannot be full fitted due to wrong
     *             request parameters or exceptions during processing
     */
    public default KuraPayload doExec(CloudletResources reqResources, KuraPayload reqPayload) throws KuraException {
        throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
    }

}
