import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeInfo {
    private int nodeId;

    private String nodeName;

    private String nodeAddress;

    private int nodePort;
}
