package vn.huuloc.apigateway.filter.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterConstants {

    public static final int WEBSOCKET_AUTH_PRECEDENCE = -3;

    public static final int RATE_LIMIT_PRECEDENCE = -2;

    public static final int RATE_LIMIT_MINUTES = 1;

    public static final int REMOVE_DUPLICATE_HEADER_PRECEDENCE = -1;
}
