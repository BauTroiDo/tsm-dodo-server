Marry = {}
local this = Marry;

this.partnerId = 0; --伴侶ID
this.partner = nil; --伴侶資料(PlayerInfo)
this.marryDate = nil; --結婚日期
this.celebrationLv = 0; --婚禮等級

this.partners = {};

function Marry.AddPartner(roleAId, roleBId)

end

function Marry.ShowFirework(x, y, interval)
  if interval == nil then
    interval = 80;
  end

  if interval < 76 then return end
  
  local effectLight = nil;

  effectLight = EffectLight.New(10001, 30, 15, 1, 15, EEffectLightTracer.StandAni, x, y + 87, 10, nil, nil, nil, nil, nil, nil, true);
  effectLight:SetSortingOrder(MapManager.mapHeight);

  effectLight = EffectLight.New(10248, interval, 1, 6, 6, EEffectLightTracer.StandAni, x + 6, y, 255, false, 68, nil, nil, nil, nil, true);
  effectLight:SetSortingOrder(MapManager.mapHeight);
  effectLight.endCallback = function(self)
    this.ShowFirework(x + math.random(-50, 50), y + math.random(-50, 50), self.interval - 1);
  end
end