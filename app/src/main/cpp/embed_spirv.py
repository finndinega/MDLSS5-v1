import pathlib, struct, sys
src=pathlib.Path(sys.argv[1]).read_bytes()
if len(src)%4: raise SystemExit("SPIR-V size is not word aligned")
words=struct.unpack("<"+"I"*(len(src)//4),src)
out=pathlib.Path(sys.argv[2])
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text("#pragma once\n#include <cstdint>\nstatic const uint32_t MDLSS5_SPV[] = {"+",".join("0x%08xu"%x for x in words)+"};\nstatic const unsigned int MDLSS5_SPV_SIZE = sizeof(MDLSS5_SPV);\n")
