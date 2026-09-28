WorldBoss = {}
local this = WorldBoss;

this.active = false;
this.actTime = nil; --DateTime
this.revTime = nil; --DateTime
this.topScore = 0;
this.newScore = 0;
this.summationScore = 0;
this.scoreRateItemId = 0;
this.waitUpdateTick = 0;
this.rank = 0; --自己名次
this.lastRankScore = 0; --前一名分數
this.ticketItemId = 46614;

function WorldBoss.Clear()
  this.active = false;
end