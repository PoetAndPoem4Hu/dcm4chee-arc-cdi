/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2012
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.stgcmt.scp;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.IApplicationEntityCache;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.AbstractDicomService;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.ArchiveAEExtension;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@ApplicationScoped
@Typed(DicomService.class)
public class StgCmtSCP extends AbstractDicomService {

    @Inject
    private StgCmtService stgCmtService;

    @Inject
    private IApplicationEntityCache aeCache;

    public StgCmtSCP() {
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    @Override
    public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse,
            Attributes rq, Attributes actionInfo) throws IOException {
        if (dimse != Dimse.N_ACTION_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        int actionTypeID = rq.getInt(Tag.ActionTypeID, 0);
        if (actionTypeID != 1)
            throw new DicomServiceException(Status.NoSuchActionType)
                        .setActionTypeID(actionTypeID);

        Attributes rsp = Commands.mkNActionRSP(rq, Status.Success);
        String localAET = as.getLocalAET();
        String remoteAET = as.getRemoteAET();
        try {
            ApplicationEntity ae = as.getApplicationEntity();
            ApplicationEntity remoteAE = aeCache
                    .findApplicationEntity(remoteAET);
            ae.findCompatibelConnection(remoteAE);
            Attributes eventInfo = stgCmtService.calculateResult(actionInfo);
            ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
            stgCmtService.scheduleNEventReport(localAET, remoteAET, eventInfo, 0,
                    aeExt != null ? aeExt.getStorageCommitmentDelay() * 1000L : 0);
        } catch (IncompatibleConnectionException e) {
            throw new DicomServiceException(Status.ProcessingFailure,
                    "No compatible connection to " + remoteAET);
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.ProcessingFailure,
                    "Unknown Calling AET: " + remoteAET);
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        as.tryWriteDimseRSP(pc, rsp, null);
    }



}
