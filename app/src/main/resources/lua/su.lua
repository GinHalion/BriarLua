local Runtime = luajava.bindClass "java.lang.Runtime"
local DataOutputStream = luajava.bindClass "java.io.DataOutputStream"
local DataInputStream = luajava.bindClass "java.io.DataInputStream"
local InputStreamReader = luajava.bindClass "java.io.InputStreamReader"
local BufferedReader = luajava.bindClass "java.io.BufferedReader"

return function(...)
    local args = {...}
    local result = { lines = {}, error = nil }

    local ok, err = pcall(function()
        local process = Runtime.getRuntime().exec("su")
        local dos = DataOutputStream(process.getOutputStream())
        local writer = luajava.newInstance("java.io.OutputStreamWriter", dos, "UTF-8")

        for _, v in ipairs(args) do
            writer.write(tostring(v) .. "\n")
            writer.flush()
        end
        writer.write("exit\n")
        writer.flush()
        writer.close()
        dos.close()

        process.waitFor()

        local stdout = BufferedReader(InputStreamReader(process.getInputStream(), "UTF-8"))
        local line
        while true do
            line = stdout.readLine()
            if line == nil then break end
            table.insert(result.lines, line)
        end
        stdout.close()

        local stderr = BufferedReader(InputStreamReader(process.getErrorStream(), "UTF-8"))
        local errLines = {}
        while true do
            line = stderr.readLine()
            if line == nil then break end
            table.insert(errLines, line)
        end
        stderr.close()

        if #errLines > 0 then
            result.error = table.concat(errLines, "\n")
        end
    end)

    if not ok then
        result.error = tostring(err)
    end

    if result.error then
        return nil, result.error
    end
    return table.concat(result.lines, "\n")
end