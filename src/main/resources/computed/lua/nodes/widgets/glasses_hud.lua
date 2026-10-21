local node = computed.node(1, "computed:glasses_hud", "Glasses HUD")

node:category("widgets")
node:style("sink")
node:execution("tick")
node:on_run(function(ctx)
    local widgets = {}
    local inputs = ctx:inputs()
    for index = 1, 16 do
        local widget = inputs["widget_" .. index]
        if widget ~= nil then
            widgets[#widgets + 1] = widget
        end
    end
    ctx:endpoint("computed:glasses"):call("show", widgets)
end)

return node
