local node = computed.node(1, "example:text_widget", "Text Widget")

node:category("widgets")
node:input("text", "string")
node:output("widget", "widget")
node:on_run(function(ctx)
    local widgets = ctx:endpoint("computed:widget")
    ctx:output("widget", widgets:call("text", ctx:input("text"), 0xffffffff))
end)

return node
