package com.payline.payment.p24.service;


import com.payline.payment.p24.bean.rest.P24RegisterRequest;
import com.payline.payment.p24.errors.P24ValidationException;
import com.payline.payment.p24.utils.*;
import com.payline.pmapi.bean.common.FailureCause;
import com.payline.pmapi.bean.payment.request.PaymentRequest;
import com.payline.pmapi.bean.payment.response.PaymentResponse;
import com.payline.pmapi.bean.payment.response.PaymentResponseFailure;
import com.payline.pmapi.bean.payment.response.PaymentResponseRedirect.PaymentResponseRedirectBuilder;
import com.payline.pmapi.bean.payment.response.PaymentResponseRedirect.RedirectionRequest;
import com.payline.pmapi.service.PaymentService;
import okhttp3.Response;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.regex.Matcher;

public class PaymentServiceImpl implements PaymentService {

    private P24HttpClient p24HttpClient;

    private RequestUtils requestUtils;

    public PaymentServiceImpl() throws GeneralSecurityException {
        p24HttpClient = new P24HttpClient();
        requestUtils = new RequestUtils();
    }

    @Override
    public PaymentResponse paymentRequest(PaymentRequest paymentRequest) {
        try {
            // create the P24 request
            P24RegisterRequest registerRequest = new P24RegisterRequest(paymentRequest);
            Map<String, String> body = registerRequest.createBodyMap();

            boolean isSandbox = requestUtils.isSandbox(paymentRequest);

            // do the request
            String host = P24Url.REST_HOST.getUrl(isSandbox);
            Response response = p24HttpClient.doPost(host, P24Path.REGISTER, body);

            if (response.code() == 200 && response.body() != null) {
                String responseMessage = response.body().string();

                // parse the result
                // no error
                if (responseMessage.startsWith("error=0")) {
                    // get the token
                    Matcher t = P24Constants.REGEX_TOKEN.matcher(responseMessage);
                    t.find();

                    String token = t.group(1);


                    // create url from the token
                    URL checkOutUrl = new URL(
                            P24Constants.SCHEME, P24Url.REST_HOST.getUrl(isSandbox), P24Path.REQUEST.getPath() + token);
                    RedirectionRequest redirectionRequest = new RedirectionRequest(checkOutUrl);

                    return PaymentResponseRedirectBuilder.aPaymentResponseRedirect()
                            .withRedirectionRequest(redirectionRequest)
                            .build();
                }
                // one or more errors
                else {
                    return getPaymentResponseFailure(P24Constants.NO_ERROR_CODE, FailureCause.INVALID_DATA);
                }
            } else {
                // wrong response code
                return getPaymentResponseFailure(P24Constants.NO_ERROR_CODE, FailureCause.COMMUNICATION_ERROR);
            }
        } catch (IOException e) {
            return getPaymentResponseFailure(P24Constants.NO_ERROR_CODE, FailureCause.INTERNAL_ERROR);
        } catch (P24ValidationException e) {
            return getPaymentResponseFailure(e.getMessage(), FailureCause.INVALID_DATA);
        }
    }

    private PaymentResponseFailure getPaymentResponseFailure(String errorCode, final FailureCause failureCause) {
        return PaymentResponseFailure.PaymentResponseFailureBuilder.aPaymentResponseFailure()
                .withFailureCause(failureCause)
                .withErrorCode(errorCode).build();
    }
}
