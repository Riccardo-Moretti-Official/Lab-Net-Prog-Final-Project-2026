package p4_aas.Submodels.NetworkInfrastructure;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.basyx.submodel.metamodel.map.submodelelement.SubmodelElement;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;

import p4_aas.Submodels.SwitchRuntime.SwitchCliClient;

public class NetworkInfrastructureMirrorLambda{
    private final SwitchCliClient switchCliClient;

    public NetworkInfrastructureMirrorLambda() {
        this.switchCliClient = new SwitchCliClient();
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> setMirrorState() {
        return args -> {
            int switchId = getInt(args, "Switch");
            boolean enabled = getBoolean(args, "Enabled");
            String command;

            if (enabled) {
                command = "table_add mirror_table mirror_to_observer 4 =>";
            } else {
                command = "table_clear mirror_table";
            }

            String output = switchCliClient.runCliCommand(switchId, command);
            return output(output);
        };
    }

    private SubmodelElement[] output(String value) {
        return new SubmodelElement[] {
            new Property("Output", value)
        };
    }

    private int getInt(Map<String, SubmodelElement> args, String name) {
        Object value = args.get(name).getValue();
        if (value instanceof BigInteger) {
            return ((BigInteger) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean getBoolean(Map<String, SubmodelElement> args, String name) {
        Object value = args.get(name).getValue();
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
