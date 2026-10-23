local node = computed.node(1, "example:terminal_widget", "Terminal")

node:category("widgets")
node:input("heading", "string", { default = "Computed OS" })
node:output("widget", "widget")
node:on_run(function(ctx)
    local widgets = ctx:endpoint("computed:widget")
    local rows = {
        widgets:call("line", ctx:input("heading"), 0xff55ffff, 0xff000000),
        "ready"
    }
    ctx:output("widget", widgets:call("terminal", rows, 0xffffffff, 0xff000000, 1, -1, -1))
end)

return node
