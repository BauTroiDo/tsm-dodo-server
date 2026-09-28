Migrant = {}
local this = Migrant;

--const
this.galaxySerHead = 90;
this.galaxySerTail = 99;

function Migrant.IsGalaxyServer(serverId)
  if serverId > 100 then
    serverId = serverId % 100;
  end

  return Between(serverId, this.galaxySerHead, this.galaxySerTail);
end

function Migrant.IsArenaScene(sceneId)
  return Contains(sceneId, 54901, 54902, 54903, 54904);
end