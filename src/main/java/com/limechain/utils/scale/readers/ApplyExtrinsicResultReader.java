package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.ApplyExtrinsicResult;
import com.limechain.transaction.dto.DispatchOutcome;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

public class ApplyExtrinsicResultReader implements ScaleReader<ApplyExtrinsicResult> {

    @Override
    public ApplyExtrinsicResult read(ScaleCodecReader reader) {
        ApplyExtrinsicResult response = new ApplyExtrinsicResult();

        if (ScaleUtils.isScaleResultSuccessful(reader)) {
            boolean isOutcomeValid = ScaleUtils.isScaleResultSuccessful(reader);
            response.setOutcome(new DispatchOutcome(isOutcomeValid));
        } else {
            response.setValidityError(new TransactionValidityErrorReader().read(reader));
        }

        return response;
    }
}
