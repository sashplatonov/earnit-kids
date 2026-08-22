package com.sashplatonov.earnit.kids.family.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateChildSettingsRequest(
    @NotBlank(message = "{validation.child.name.required}")
    @Size(max = 50, message = "{validation.child.name.max}")
    String name,

    @NotNull(message = "{validation.daily.coin.limit.required}")
    @Min(value = 0, message = "{validation.daily.coin.limit.min}")
    @JsonAlias("daily_coin_limit")
    Integer dailyCoinLimit,

    @NotNull(message = "{validation.monthly.limit.required}")
    @Min(value = 0, message = "{validation.monthly.limit.min}")
    @JsonAlias("monthly_limit")
    Integer monthlyLimit,

    @Min(value = 0, message = "{validation.daily.reward.limit.min}")
    @JsonAlias("daily_reward_limit")
    Integer dailyRewardLimit
) { }
