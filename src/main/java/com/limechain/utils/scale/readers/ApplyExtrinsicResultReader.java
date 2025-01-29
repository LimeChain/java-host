package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.ApplyExtrinsicResult;
import com.limechain.transaction.dto.DispatchOutcome;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplyExtrinsicResultReader implements ScaleReader<ApplyExtrinsicResult> {

    private static final ApplyExtrinsicResultReader INSTANCE = new ApplyExtrinsicResultReader();

    public static ApplyExtrinsicResultReader getInstance() {
        return INSTANCE;
    }

    @Override
    public ApplyExtrinsicResult read(ScaleCodecReader reader) {
        ApplyExtrinsicResult response = new ApplyExtrinsicResult();

        if (ScaleUtils.isScaleResultSuccessful(reader)) {
            boolean isOutcomeValid = ScaleUtils.isScaleResultSuccessful(reader);
            response.setOutcome(new DispatchOutcome(isOutcomeValid));
        } else {
            response.setValidityError(TransactionValidityErrorReader.getInstance().read(reader));
        }

        return response;
    }
}
