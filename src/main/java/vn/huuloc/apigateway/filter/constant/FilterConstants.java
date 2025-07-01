package vn.huuloc.apigateway.filter.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterConstants {

    public static final int RATE_LIMIT_PRECEDENCE = -2;
    public static final int JWT_TOKEN_PRECEDENCE = -1;

    public static final int RATE_LIMIT_MINUTES = 1;
}
